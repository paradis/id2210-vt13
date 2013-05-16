/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import search.system.peer.leader.LeaderMsg.Accept;
import search.system.peer.leader.LeaderMsg.AskLeaderInfos;
import search.system.peer.leader.LeaderMsg.Reject;
import search.system.peer.leader.LeaderMsg.AnswerLeaderInfos;
import search.system.peer.search.SearchInit;
import tman.system.peer.tman.TManSample;

/*
 * idées: merger les messages bestpeer / leader
 * on veut un leader vivant, donc quand on recoit un message du leader
 * 3 requetes en parallèle:
 *  dès réception d'une, si on a pas le leader et que la demande (auto ou master) est valide, on merge les best peers et on contacte le meilleur qui n'a pas été contacté.
 *
 *
 * accélérer convergence: leader election toutes les 100sec, avec démarrage aléatoire.
 *
 */
/**
 *
 * @author alban
 */
public class LeaderElector extends ComponentDefinition{
    private static final Logger logger = LoggerFactory.getLogger(LeaderElector.class);

    Negative<LeaderElectionPort> leaderElectionPort = negative(LeaderElectionPort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);

    List<Address> tmanNeighbours = null;
    Address currentLeader = null;
    Address oldLeader = null;
    TreeSet<Address> bestPeers;    // Known peer with highest utility
    Address self;
    Map<Address, UUID> currentRequests;

    List<Address> expectedElectors = null;  // List of electors whose answer is awaited ; must be null if no ongoing election

    public LeaderElector() {
        bestPeers = new TreeSet<Address>(new ComparatorAddressById());
        currentRequests = new HashMap<Address, UUID>();

        subscribe(handleInit, control);

        subscribe(handleLeaderRequest, leaderElectionPort);
        subscribe(handleTManSample, leaderElectionPort);

        subscribe(handleLeaderApply, networkPort);
        subscribe(handleLeaderAccept, networkPort);
        subscribe(handleLeaderReject, networkPort);
        subscribe(handleElectionTimeout, timerPort);

        subscribe(handleAskLeaderInfos, networkPort);
        subscribe(handleAnswerLeaderInfos, networkPort);
        subscribe(handleLeaderInfosTimeout, timerPort);
    }

    Handler<SearchInit> handleInit = new Handler<SearchInit>() {
        @Override
        public void handle(SearchInit init) {
            self = init.getSelf();
        }
    };

    // The peer ask for the leader
    Handler<LeaderRequest> handleLeaderRequest = new Handler<LeaderRequest>() {
        @Override
        public void handle(LeaderRequest e) {
            //here we check if the leader is up
            if (currentLeader == self)
                trigger(new LeaderResponse(self), leaderElectionPort);
            else if (currentLeader != null)
                launchRequest(currentLeader);
            else
                launchRequests();

        }
    };

    /*
     * another peer's LeaderElector wants to know our leader and best peers
     */
    Handler<LeaderMsg.AskLeaderInfos> handleAskLeaderInfos = new Handler<LeaderMsg.AskLeaderInfos>() {
        @Override
        public void handle(AskLeaderInfos e) {
            if (e.getSuspectedLeader() != null && e.getSuspectedLeader() == currentLeader)
            {
                launchRequest(currentLeader);
                trigger(new LeaderMsg.AnswerLeaderInfos(self, e.getSource(), null, bestPeers), networkPort);
            }
            else
                trigger(new LeaderMsg.AnswerLeaderInfos(self, e.getSource(), currentLeader, bestPeers), networkPort);
        }
    };

    /*
     * we receive a leader and a list of best peers
     */
    Handler<LeaderMsg.AnswerLeaderInfos> handleAnswerLeaderInfos = new Handler<LeaderMsg.AnswerLeaderInfos>() {
        @Override
        public void handle(AnswerLeaderInfos e) {
            trigger (new CancelTimeout(currentRequests.get(e.getSource())), timerPort);
            currentRequests.remove(e.getSource());

            bestPeers.addAll(e.getBestPeers());
            cleanBestPeers();

            if (e.getCurrentLeader() != null)
            {
                // We receive a message from the leader, it is up
                if (e.getCurrentLeader() == e.getSource())
                {
                    trigger (new LeaderResponse(currentLeader), leaderElectionPort);
                    currentLeader = e.getCurrentLeader();
                }
                else
                {
                    if (currentLeader == e.getSource())
                        currentLeader = null;

                    bestPeers.add(e.getCurrentLeader());
                    launchRequest(e.getCurrentLeader());
                }
            }
            else
                launchRequests();

        }
    };

    Handler<LeaderInfosTimeout> handleLeaderInfosTimeout = new Handler<LeaderInfosTimeout>() {
        @Override
        public void handle(LeaderInfosTimeout e) {

            Address address = getKeyFromValue(e.getTimeoutId());

            if (address != null)
            {
                currentRequests.remove(address);

                if (currentLeader == address)
                {
                    oldLeader = currentLeader;
                    currentLeader = null;
                }

                bestPeers.remove(address);
            }

            launchRequests();

            // We have already signaled that our leader were suspected to at most 3 best Peers; no need to continue
            oldLeader = null;
        }
    };


    /*
     * Lauch a request for the leader and some best peers to the best peers we know
     */
    void launchRequests()
    {
        while(currentLeader == null && currentRequests.size() < 4)
        {
            Address peer = selectNextPeer();

            if (peer != null)
                launchRequest(peer);
            else
            {
                // we have no possibility to find a leader
                if (currentRequests.isEmpty())
                    trigger(new LeaderResponse(null), leaderElectionPort);

                return;
            }
        }
    }

    /*
     * Select a peer which may be contacted
     * TODO: à vérifier, en particulier itération
     */
    Address selectNextPeer() {
        for (Address peer : bestPeers) {
            if (!currentRequests.containsKey(peer))
                return peer;
        }

        return null;
    }

    /*
     * Ask for leader and peers to a peer
     */
    void launchRequest(Address peer)
    {
        if (currentRequests.containsKey(peer))
            return;

        trigger(new LeaderMsg.AskLeaderInfos(self, peer, oldLeader), networkPort);

        LeaderInfosTimeout timeout = new LeaderInfosTimeout(new ScheduleTimeout(2000));
        trigger(timeout, timerPort);
        currentRequests.put(peer, timeout.getTimeoutId());
    }

    Handler<TManSample> handleTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {
            if (event.getSample().isEmpty())
                return;

            bestPeers.addAll(event.getSample());
            cleanBestPeers();

            tmanNeighbours = event.getSample();
            Address bestNeighbour = getBestPeerFrom(tmanNeighbours, null);
            boolean noLargerId = bestNeighbour.getId() <= self.getId();

            if (!noLargerId && currentLeader == self) {
                // Do not consider myself the leader any more
                currentLeader = null;
                logger.debug(self.getId()+" : "+bestNeighbour.getId()+" discovered as better node, so I abdicate.");
            }

            if(noLargerId) {
                logger.debug(self.getId()+" : no larger id found among "+printAdresses(tmanNeighbours));
            }

            // If noLargerId is true and no leader is known and no election is going on, launch an election
            if (noLargerId && currentLeader == null && expectedElectors == null) {
                logger.debug(self.getId()+" : launch election.");
                launchElection();
            }
        }
    };

    public void launchElection() {
        // Ask all tman neighbours if I can be the leader
        for (Address dest : tmanNeighbours) {
            trigger (new LeaderMsg.Apply(self, dest), networkPort);
        }

        expectedElectors = tmanNeighbours;
        // TODO : adapt timeout
        ScheduleTimeout rst = new ScheduleTimeout(10000);
        rst.setTimeoutEvent(new ElectionTimeout(rst));
        trigger(rst, timerPort);
    }

    Handler<LeaderMsg.Apply> handleLeaderApply = new Handler<LeaderMsg.Apply>() {
        @Override
        public void handle(LeaderMsg.Apply e) {
            // The source of this message is applying for leadership

            if (currentLeader != null && currentLeader.getId() > e.getSource().getId()) {
                trigger(new LeaderMsg.Reject(self, e.getSource(), currentLeader), networkPort);
            }
            else if (tmanNeighbours == null) {
                trigger(new LeaderMsg.Accept(self, e.getSource()), networkPort);
            }
            else {
                Address bestVote = getBestPeerFrom(tmanNeighbours, e.getSource());

                if (bestVote == e.getSource()) {
                    trigger(new LeaderMsg.Accept(self, e.getSource()), networkPort);
                }
                else {
                    trigger(new LeaderMsg.Reject(self, e.getSource(), bestVote), networkPort);
                }
            }
        }
    };

    Handler<LeaderMsg.Accept> handleLeaderAccept = new Handler<LeaderMsg.Accept>() {
        @Override
        public void handle(Accept e) {
            // This elector has accepted the election, so don't expect anything from him any more.
            if (expectedElectors != null) {
                logger.debug(self.getId()+" : election : received acceptance message from "+e.getSource().getId());
                expectedElectors.remove(e.getSource());
                if (expectedElectors.isEmpty()) {
                    currentLeader = self;
                    expectedElectors = null;
                    logger.debug(self.getId()+" : election : I got elected !");
                }
            }
        }
    };

    Handler<LeaderMsg.Reject> handleLeaderReject = new Handler<LeaderMsg.Reject>() {
        @Override
        public void handle(Reject e) {
            // Someone rejected the election ! Abort.
            logger.debug(self.getId()+" : election : received rejection message from "+e.getSource().getId());
            expectedElectors = null;

            // TODO : Contact preferred peer
        }
    };

    Handler<ElectionTimeout> handleElectionTimeout = new Handler<ElectionTimeout>() {
        @Override
        public void handle(ElectionTimeout e) {
            logger.debug(self.getId()+" : election : timeout");
            // If election hasn't been completed yet...
            if (expectedElectors != null) {
                logger.debug(self.getId()+" : election : I got elected !");
                // ... then I am the new leader
                currentLeader = self;
                expectedElectors = null;
            }
        }
    };

    Address getBestPeerFrom(List<Address> peers, Address other) {
        if (peers == null || peers.isEmpty()) {
            return other;
        }

        Address best = peers.get(0);
        for (Address a : peers) {
            if (a.getId() > best.getId()) {
                best = a;
            }
        }

        if (other == null) {
            return best;
        }

        if (other.getId() > best.getId()) {
            return other;
        }
        else {
            return best;
        }
    }

    // For debug purposes
    String printAdresses(List<Address> list)
    {
        String str = "[";
        for (Address d : list)
            str += d.getId()+"; ";
        return str + "]";
    }

    void cleanBestPeers()
    {
        bestPeers.remove(self);

        while(bestPeers.size() > 10)
            bestPeers.pollLast();
    }

    Address getKeyFromValue(UUID value) {
        for (Address a : currentRequests.keySet())
            if (currentRequests.get(a) == value)
                return a;

        return null;
    }
}

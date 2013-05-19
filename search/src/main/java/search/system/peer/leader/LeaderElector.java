/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import search.system.peer.leader.LeaderMsg.Accept;
import search.system.peer.leader.LeaderMsg.AnswerLeaderInfos;
import search.system.peer.leader.LeaderMsg.AskLeaderInfos;
import search.system.peer.leader.LeaderMsg.Reject;
import search.system.peer.search.SearchInit;
import tman.system.peer.tman.TManSample;

/**
 * This class is responsible for electing a leader and return a functional leader.
 * It will maintain a list of peers close to the leader and contact them when performing a search of a leader.
 */
public class LeaderElector extends ComponentDefinition{
    private static final Logger logger = LoggerFactory.getLogger(LeaderElector.class);

    //TODO : put all that in a configuration class
    private static final int config_timeout_election = 10000;
    private static final int config_timeout_info = 2000;
    private static final int config_max_requests = 3;
    private static final int config_size_bestPeers = 10;
    private static final int config_timeout_update = 60000;
    private static final int config_delay_tryagain = 4000; //When we have no peer and a leader is requested

    Negative<LeaderElectionPort> leaderElectionPort = negative(LeaderElectionPort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);

    Address self;
    
    ///
    // Leader research
    ///
    
    List<Address> tmanNeighbours = null;
    // Known peers with highest utility
    ConcurrentSkipListSet<Address> bestPeers;
    // Current requests for leader infos
    Map<Address, UUID> currentRequests;

    // Current living leader
    Address currentLeader = null;
    // The old leader which is believed to be offline to notify other nodes and avoid that they give it back to us.
    Address oldLeader = null;
    // If our peer is waiting to know who the leader is (to differentiate it from an automatic task)
    boolean peerRequest = false;

    ///
    // Leader election
    /// 
    List<Address> expectedElectors = null;  // List of electors whose answer is awaited ; must be null if no ongoing election

    public LeaderElector() {
        bestPeers = new ConcurrentSkipListSet<Address>(new ComparatorAddressById());
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

        subscribe(handleUpdateLeaderTimeout, timerPort);
    }

    Handler<SearchInit> handleInit = new Handler<SearchInit>() {
        @Override
        public void handle(SearchInit init) {
            self = init.getSelf();

            ///
            // Regularly, we will try to find out who the leader to accelerate convergence in a request by our peer.
            // To ensure that all nodes make their request at the same time, the launch is shifted randomly.
            ///
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(new Random().nextInt(config_timeout_update), config_timeout_update);
            rst.setTimeoutEvent(new UpdateLeaderTimeout(rst));
            trigger(rst, timerPort);
        }
    };

//------------------------------------------------------------------------------
//                 Leader Research
//------------------------------------------------------------------------------
    
    //--------------------------------------------------------------------------
    // Lauch a leader request
    
    /*
     * The peer ask for the leader
     */
    Handler<LeaderRequest> handleLeaderRequest = new Handler<LeaderRequest>() {
        @Override
        public void handle(LeaderRequest e) {
            if (peerRequest) {
                return;
            }
            
            peerRequest = true;

            // we are the leader, nothing else to do
            if (currentLeader == self)
            {
                peerRequest = false;
                trigger(new LeaderResponse(self), leaderElectionPort);
                logger.debug(self.getId()+" : LeaderRequest: We are the leader");
            }
            // here we check if the leader is up
            else if (currentLeader != null) {
                askInfos(currentLeader);
            }
            // we have to search for the leader
            else {
                launchRequests();
            }

        }
    };
    
    /*
     * Request for looking for the leader.
     * It can be automatic or the result of an old inability to continue our search for lack of pee
     */
    Handler<UpdateLeaderTimeout> handleUpdateLeaderTimeout = new Handler<UpdateLeaderTimeout>() {
        @Override
        public void handle(UpdateLeaderTimeout e) {
            // If we have a leader, we check if it is online before launching requests
            if (currentLeader != null)
                askInfos(currentLeader);
            else
                launchRequests();
        }
    };
    
    //--------------------------------------------------------------------------
    // Ask info from another peer and manage response or timeout
    
    /*
     * Ask for leader and best peers to a peer and launch a timeout in case of the peer is offline
     */
    void askInfos(Address peer)
    {
        // A request is currently pending
        if (currentRequests.containsKey(peer)) {
            return;
        }

        trigger(new LeaderMsg.AskLeaderInfos(self, peer, oldLeader), networkPort);

        ScheduleTimeout st = new ScheduleTimeout(config_timeout_info);
        st.setTimeoutEvent(new LeaderInfosTimeout(st));
        currentRequests.put(peer, st.getTimeoutEvent().getTimeoutId());
        trigger(st, timerPort);
    }
    
    /*
     * Another peer's LeaderElector wants to know our leader and best peers
     */
    Handler<LeaderMsg.AskLeaderInfos> handleAskLeaderInfos = new Handler<LeaderMsg.AskLeaderInfos>() {
        @Override
        public void handle(AskLeaderInfos e) {
            if (e.getSuspectedLeader() != null && e.getSuspectedLeader() == currentLeader)
            {
                logger.debug(self.getId()+" : Leader (" + currentLeader.getId() + ") suspected by " + e.getSource().getId());
                // The peer has suspected our leader, we check if it is true
                askInfos(currentLeader);
                // And we avoi to response with a suspected leader
                trigger(new LeaderMsg.AnswerLeaderInfos(self, e.getSource(), null, bestPeers), networkPort);
            }
            else {
                trigger(new LeaderMsg.AnswerLeaderInfos(self, e.getSource(), currentLeader, bestPeers), networkPort);
            }
        }
    };

    /*
     * We receive a leader and a list of best peers.
     */
    Handler<LeaderMsg.AnswerLeaderInfos> handleAnswerLeaderInfos = new Handler<LeaderMsg.AnswerLeaderInfos>() {
        @Override
        public void handle(AnswerLeaderInfos e) {
            // We cancel the timeout associated with this request
            trigger (new CancelTimeout(currentRequests.get(e.getSource())), timerPort);
            currentRequests.remove(e.getSource());

            // We merge our best peers
            bestPeers.addAll(e.getBestPeers());
            cleanBestPeers();

            if (e.getCurrentLeader() != null)
            {
                // We receive a message from the leader, it is up
                if (e.getCurrentLeader() == e.getSource())
                {
                    currentLeader = e.getCurrentLeader();
                    logger.debug(self.getId()+" : Found Leader: " + currentLeader.getId());
                    if (peerRequest)
                    {
                        peerRequest = false;
                        trigger (new LeaderResponse(currentLeader), leaderElectionPort);
                    }
                }
                else
                {
                    // Our leader no longer considers itself as the leader anymore
                    if (currentLeader == e.getSource()) {
                        currentLeader = null;
                    }

                    // And we contact the supposed new leader
                    bestPeers.add(e.getCurrentLeader());
                    askInfos(e.getCurrentLeader());
                }
            }
            else {
                // No more information on the leader, we try to launch requests with potential new peers that we obtained
                launchRequests();
            }

        }
    };

    /*
     * A request for information has not been successful, we assume that the peer is offline and we remove from our lists.
     */
    Handler<LeaderInfosTimeout> handleLeaderInfosTimeout = new Handler<LeaderInfosTimeout>() {
        @Override
        public void handle(LeaderInfosTimeout e) {

            // retrieve the timeout associated with the request 
            Address address = getKeyFromValue(e.getTimeoutId());

            if (address == null)
                logger.error(self.getId() + " handleLeaderInfosTimeout: unknown request!");
            else
            {
                currentRequests.remove(address);

                // The leader is down
                if (currentLeader == address)
                {
                    logger.debug(self.getId()+" : No answers from leader: " + currentLeader.getId());
                    // To inform the other peers and avoid useless response with this leader
                    oldLeader = currentLeader;
                    currentLeader = null;
                }

                // This peer should not be considered anymore
                bestPeers.remove(address);
            }

            launchRequests();

            // We have already signaled that our leader were suspected to at most config_max_requests best Peers; no need to continue
            oldLeader = null;
        }
    };

        
    //--------------------------------------------------------------------------
    // Launch multiple requests since our leader is not responding anymore
    
    /*
     * Lauch requests for the leader and best peers to the best peers we know
     */
    void launchRequests()
    {
        // If the leader is still unknown and we have not exceeded our limit of concurrent requests.
        while(currentLeader == null && currentRequests.size() <= config_max_requests)
        {
            // Select a peer which may be contacted
            Address peer = selectNextPeer();

            if (peer != null)
                askInfos(peer);
            else
            {
                // we have no possibility to find a leader, so we wait some time and try again
                if (currentRequests.isEmpty())
                {
                    ScheduleTimeout rst = new ScheduleTimeout(config_delay_tryagain);
                    rst.setTimeoutEvent(new UpdateLeaderTimeout(rst));
                    trigger(rst, timerPort);
                }

                return;
            }
        }
    }

    /*
     * Select a peer which may be contacted:
     * We select the best peer we known which is not currently contacted
     */
    Address selectNextPeer() {
        for (Address peer : bestPeers)
            if (!currentRequests.containsKey(peer))
                return peer;

        return null;
    }

    
    //--------------------------------------------------------------------------
    // Refresh information with TMan 
    
    Handler<TManSample> handleTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {
            if (event.getSample().isEmpty()) {
                return;
            }

            bestPeers.addAll(event.getSample());
            cleanBestPeers();

            tmanNeighbours = event.getSample();
            Address bestNeighbour = getBestPeerFrom(tmanNeighbours, null);
            boolean noLargerId = bestNeighbour.getId() <= self.getId();

            if (!noLargerId && currentLeader == self) {
                // Do not consider myself the leader any more
                currentLeader = null;
                logger.debug(self.getId()+" : "+bestNeighbour.getId()+" discovered as better node, so I abdicate.");
                trigger(new LeaderElectionNotify(self), leaderElectionPort);
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
    
    /*
     * Utility function: remove some best peers after merging other LeaderElector's best peers
     */
    void cleanBestPeers()
    {
        bestPeers.remove(self);

        while(bestPeers.size() >= config_size_bestPeers) {
            bestPeers.pollLast();
        }
    }
    
//------------------------------------------------------------------------------
//                 Leader election
//------------------------------------------------------------------------------
   
    public void launchElection() {
        // Ask all tman neighbours if I can be the leader
        for (Address dest : tmanNeighbours) {
            trigger (new LeaderMsg.Apply(self, dest), networkPort);
        }

        expectedElectors = tmanNeighbours;
        ScheduleTimeout rst = new ScheduleTimeout(config_timeout_election);
        rst.setTimeoutEvent(new ElectionTimeout(rst));
        trigger(rst, timerPort);
    }

    Handler<LeaderMsg.Apply> handleLeaderApply = new Handler<LeaderMsg.Apply>() {
        @Override
        public void handle(LeaderMsg.Apply e) {
            // The source of this message is applying for leadership

            if (currentLeader != null && currentLeader.getId() > e.getSource().getId()) {
                // check if leader is still up
                askInfos(currentLeader);
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
                    // Signal your peer that he is the new leader (even though he didn't ask for it), and give him the lod leader as well.
                    trigger(new LeaderElectionNotify(currentLeader), leaderElectionPort);
                    
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

            askInfos(e.getBetterPeer());
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

    /*
     * Return best peer (max Id) from a list of peer and another address
     */
    Address getBestPeerFrom(List<Address> peers, Address other) {
        if (peers == null)
            return other;
        
        ArrayList<Address> myPeers = new ArrayList<Address>(peers);
        myPeers.add(other);
        
        Address best = myPeers.get(0);
        for (Address a : myPeers) {
            if (a.getId() > best.getId()) {
                best = a;
            }
        }
        
        return best;
    }

    // For debug purposes
    String printAdresses(List<Address> list)
    {
        String str = "[";
        for (Address d : list) {
            str += d.getId()+"; ";
        }
        return str + "]";
    }

    Address getKeyFromValue(UUID value) {
        for (Address a : currentRequests.keySet()) {
            if (currentRequests.get(a) == value) {
                return a;
            }
        }

        return null;
    }
}

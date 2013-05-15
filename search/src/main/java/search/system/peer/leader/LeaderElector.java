/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import java.util.List;
import java.util.UUID;
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
import search.system.peer.leader.LeaderMsg.AskCurrentLeader;
import search.system.peer.leader.LeaderMsg.Reject;
import search.system.peer.leader.LeaderMsg.SendBestPeer;
import search.system.peer.leader.LeaderMsg.SendCurrentLeader;
import search.system.peer.search.SearchInit;
import tman.system.peer.tman.TManSample;

/**
 *
 * @author alban
 */
public class LeaderElector extends ComponentDefinition{
    Negative<LeaderElectionPort> leaderElectionPort = negative(LeaderElectionPort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);

    List<Address> tmanNeighbours = null;
    Address currentLeader = null;
    Address bestPeer = null;    // Known peer with highest utility
    Address self;
    UUID currentRequestTimeoutID = null;
    
    List<Address> expectedElectors = null;  // List of electors whose answer is awaited ; must be null if no ongoing election
    
    public LeaderElector() {
        subscribe(handleInit, control);
        subscribe(handleLeaderRequest, leaderElectionPort);
        subscribe(handleTManSample, leaderElectionPort);
        subscribe(handleLeaderApply, networkPort);
        subscribe(handleLeaderAccept, networkPort);
        subscribe(handleLeaderReject, networkPort);
        subscribe(handleElectionTimeout, timerPort);
    }
        
    Handler<SearchInit> handleInit = new Handler<SearchInit>() {
        @Override
        public void handle(SearchInit init) {
            self = init.getSelf();
        }
    };
    
    Handler<LeaderRequest> handleLeaderRequest = new Handler<LeaderRequest>() {
        @Override
        public void handle(LeaderRequest e) {
            if (currentLeader != null) {
                trigger (new LeaderResponse(currentLeader), leaderElectionPort);
            }
            else {
                // If I am already looking for the leader, it's useless to look twice.
                if (currentRequestTimeoutID != null) {
                    return;
                }
            
                if (bestPeer != null) {
                    trigger (new LeaderMsg.AskCurrentLeader(self, bestPeer), networkPort);
                    
                    LeaderRequestTimeout timeout = new LeaderRequestTimeout(new ScheduleTimeout(2000));
                    trigger(timeout, timerPort);
                    currentRequestTimeoutID = timeout.getTimeoutId();
                }
                else {
                    trigger (new LeaderResponse(null), leaderElectionPort);
                }
                /*// No known leader. Find highest id among neighbours
                Address bestNeighbour = getBestPeerFrom(tmanNeighbours, null);
                
                // If I am the largest node, start election
                if (bestNeighbour.getId() <= self.getId()) {
                    launchElection();
                }
                else {
                    trigger (new LeaderMsg.AskCurrentLeader(self, bestNeighbour), networkPort);
                    // TODO : trigger timeout
                }*/
            }
        }
    };
    
    Handler<LeaderMsg.AskCurrentLeader> handleAskCurrentLeader = new Handler<LeaderMsg.AskCurrentLeader>() {
        @Override
        public void handle(AskCurrentLeader e) {
            if (currentLeader != null) {
                trigger(new LeaderMsg.SendCurrentLeader(self, e.getSource(), currentLeader), networkPort);
            }
            else {
                trigger(new LeaderMsg.SendBestPeer(self, e.getSource(), bestPeer), networkPort);
            }
        }
    };
    
    Handler<LeaderMsg.SendCurrentLeader> handleSendCurrentLeader = new Handler<LeaderMsg.SendCurrentLeader>() {
        @Override
        public void handle(SendCurrentLeader e) {
            // TODO : checks ?
            currentLeader = e.getCurrentLeader();
            trigger (new LeaderResponse(currentLeader), leaderElectionPort);
            trigger (new CancelTimeout(currentRequestTimeoutID), timerPort);
            currentRequestTimeoutID = null;
        }
    };
    
    Handler<LeaderMsg.SendBestPeer> handleSendBestPeer = new Handler<LeaderMsg.SendBestPeer>() {
        @Override
        public void handle(SendBestPeer e) {
            // TODO : checks ?
            trigger (new CancelTimeout(currentRequestTimeoutID), timerPort);
            
            if (bestPeer != null && bestPeer.getId() < e.getBestPeer().getId()) {
                bestPeer = e.getBestPeer();
                trigger (new LeaderMsg.AskCurrentLeader(self, bestPeer), networkPort);
                
                LeaderRequestTimeout timeout = new LeaderRequestTimeout(new ScheduleTimeout(2000));
                trigger(timeout, timerPort);
                currentRequestTimeoutID = timeout.getTimeoutId();
            }
        }
    };
    
    Handler<LeaderRequestTimeout> handleLeaderRequestTimeout = new Handler<LeaderRequestTimeout>() {
        @Override
        public void handle(LeaderRequestTimeout e) {
            if (e.getTimeoutId() == currentRequestTimeoutID) {
                // Request timeout : abandon operation
                currentRequestTimeoutID = null;
                trigger(new LeaderResponse(null), leaderElectionPort);
                // Best peer didn't answer : assume he's gone.
                bestPeer = null;
            }
        }
    };
    
    Handler<TManSample> handleTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {
            tmanNeighbours = event.getSample();
            bestPeer = getBestPeerFrom(tmanNeighbours, bestPeer);
            
            Address bestNeighbour = getBestPeerFrom(tmanNeighbours, null);
            boolean noLargerId = bestNeighbour.getId() <= self.getId();
            
            // If noLargerId is true and no leader is known and no election is going on, launch an election
            if (noLargerId && currentLeader == null && expectedElectors == null) {
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
                expectedElectors.remove(e.getSource());
                if (expectedElectors.isEmpty()) {
                    currentLeader = self;
                    expectedElectors = null;
                }
            }
        }
    };
    
    Handler<LeaderMsg.Reject> handleLeaderReject = new Handler<LeaderMsg.Reject>() {
        @Override
        public void handle(Reject e) {
            // Someone rejected the election ! Abort.
            expectedElectors = null;
            
            // TODO : Contact preferred peer
        }
    };
    
    Handler<ElectionTimeout> handleElectionTimeout = new Handler<ElectionTimeout>() {
        @Override
        public void handle(ElectionTimeout e) {
            // If election hasn't been completed yet...
            if (expectedElectors != null) {
                // ... then I am the new leader
                currentLeader = self;
                expectedElectors = null;
            }
        }
    };
    
    Address getBestPeerFrom(List<Address> peers, Address other) {
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
}

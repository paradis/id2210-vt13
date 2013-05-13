/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import java.util.List;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import search.system.peer.leader.LeaderMsg.Accept;
import search.system.peer.leader.LeaderMsg.Reject;
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
    Address self;
    
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
            trigger (new LeaderResponse(currentLeader), leaderElectionPort);
        }
    };
    
    Handler<TManSample> handleTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {
            tmanNeighbours = event.getSample();
            
            int myId = self.getId();
            boolean noLargerId = true;
            for (Address a : tmanNeighbours) {
                if (a.getId() > myId) {
                    noLargerId = false;
                }
            }
            
            // If noLargerId is true and no leader is known and no election is going on, launch an election
            if (noLargerId && currentLeader == null && expectedElectors == null) {
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
        }
    };

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
                Address bestVote = e.getSource();
                for (Address neighbour : tmanNeighbours) {
                    if (neighbour.getId() > bestVote.getId()) {
                        bestVote = neighbour;
                    }
                }
                
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
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import java.util.List;
import java.util.SortedSet;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author alban
 *  Messages that can be exchanged by the different LeaderElector through the network to elect a leader or find informations on the current leader.
 */
public class LeaderMsg {
     public static class Apply extends Message {
        Apply(Address source, Address destination) {
            super(source, destination);
        }
    }
     
    public static class Accept extends Message {
        Accept(Address source, Address destination) {
            super(source, destination);
        }
    }
    
    public static class Reject extends Message {
        protected Address _betterPeer;
        
        Reject(Address source, Address destination, Address betterPeer) {
            super(source, destination);
            _betterPeer = betterPeer;
        }
        
        public Address getBetterPeer() {
            return _betterPeer;
        }
    }
    
    /*
     * Ask for the current leader.
     * If suspectedLeader is not null, it means that the asking node suspects this node to be dead.
     * If the answering node's currentLeader is suspectedLeader, the answering node is expected to check whether his leader is still alive.
     */
    public static class AskLeaderInfos extends Message {
        private Address _suspectedLeader;
        
        AskLeaderInfos(Address source, Address destination, Address suspectedLeader) {
            super(source, destination);
            _suspectedLeader = suspectedLeader;
        }
        
        public Address getSuspectedLeader() {
            return _suspectedLeader;
        }
        
    }
    
    /*
     * Give info concerning
     * - the current leader, if it is known to the node
     * - the best peers (those with highest ids) that the node knows
     */
    public static class AnswerLeaderInfos extends Message {
        private Address _currentLeader;
        private SortedSet<Address> _bestPeers;

        public Address getCurrentLeader() {
            return _currentLeader;
        }
        
        public SortedSet<Address> getBestPeers() {
            return _bestPeers;
        }
        
        AnswerLeaderInfos(Address source, Address destination, Address currentLeader, SortedSet<Address> bestPeers) {
            super(source, destination);
            _currentLeader = currentLeader;
            _bestPeers = bestPeers;
        }
    }
}

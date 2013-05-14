/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author alban
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
    
    public static class AskCurrentLeader extends Message {
        AskCurrentLeader(Address source, Address destination) {
            super(source, destination);
        }
    }
    
    public static class SendCurrentLeader extends Message {
        private Address _currentLeader;

        public Address getCurrentLeader() {
            return _currentLeader;
        }
        
        SendCurrentLeader(Address source, Address destination, Address currentLeader) {
            super(source, destination);
            _currentLeader = currentLeader;
        }
    }
    
    public static class SendBestPeer extends Message {
        private Address _bestPeer;

        public Address getBestPeer() {
            return _bestPeer;
        }
        
        SendBestPeer(Address source, Address destination, Address bestPeer) {
            super(source, destination);
            _bestPeer = bestPeer;
        }
    }
}

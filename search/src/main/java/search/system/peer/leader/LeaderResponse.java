/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import se.sics.kompics.Event;
import se.sics.kompics.address.Address;

/**
 *
 * @author alban
 */
public class LeaderResponse extends Event {
    Address _leader;
    
    LeaderResponse(Address leader) {
        _leader = leader;
    }
    
    Address getLeader() {
        return _leader;
    }
}

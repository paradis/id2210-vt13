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
public class LeaderElectionNotify extends Event{
    private Address _oldLeader;

    public Address getOldLeader() {
        return _oldLeader;
    }

    public LeaderElectionNotify(Address _oldLeader) {
        this._oldLeader = _oldLeader;
    }
}

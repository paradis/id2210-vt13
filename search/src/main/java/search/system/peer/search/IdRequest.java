/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.search;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author alban
 */
public class IdRequest extends Message {
    private String entry;

    public IdRequest(Address source, Address destination, String entry) {
        super(source, destination);
        this.entry = entry;
    }

    public String getEntry() {
        return entry;
    }
}

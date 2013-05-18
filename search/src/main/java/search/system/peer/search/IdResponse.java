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
public class IdResponse extends Message {
    private int entryId;
    private String entry;

    public int getEntryId() {
        return entryId;
    }

    public String getEntry() {
        return entry;
    }

    public IdResponse(Address source, Address dest, int entryId, String entry) {
        super(source, dest);
        this.entryId = entryId;
        this.entry = entry;
    }
}

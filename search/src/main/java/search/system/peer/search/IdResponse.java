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
    private int response;

    public int getResponse() {
        return response;
    }

    public IdResponse(Address source, Address dest, int response) {
        super(source, dest);
        this.response = response;
    }
}

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
    public IdRequest(Address source, Address destination) {
        super(source, destination);
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import java.util.Comparator;
import se.sics.kompics.address.Address;

/**
 * Make Node with Highest Id Leader in the Gradient
 */
public class ComparatorAddressById implements Comparator<Address> {

    @Override
    public int compare(Address o1, Address o2) {
        assert (o1.getId() == o2.getId());
        if (o1.getId() < o2.getId())
            return 1;
        else
            return -1;
    }
    
}

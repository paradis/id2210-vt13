/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import java.util.Comparator;
import se.sics.kompics.address.Address;

/**
 * Make Node with Highest Id Leader in the Gradient
 * This is a comparator class. The first node according to this order is the one with the highest id.
 * Sorted lists in java are stored in ascending order.
 * Thus, the first node in a sorted list according to this order is the one with the highest id.
 */
public class ComparatorAddressById implements Comparator<Address> {

    @Override
    public int compare(Address o1, Address o2) {
        if (o1.getId() == o2.getId())
            return 0;
        else if (o1.getId() < o2.getId())
            return 1;
        else
            return -1;
    }
}

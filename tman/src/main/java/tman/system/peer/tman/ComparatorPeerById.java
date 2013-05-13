package tman.system.peer.tman;

import cyclon.system.peer.cyclon.PeerDescriptor;
import java.util.Comparator;
import se.sics.kompics.address.Address;

/**
 * Make Node with Highest Id Leader in the Gradient
 */
public class ComparatorPeerById implements Comparator<PeerDescriptor> {
    Comparator<Address> comp;

    public ComparatorPeerById(Address self) {
        this.comp = new ComparatorById(self);
    }

    @Override
    public int compare(PeerDescriptor o1, PeerDescriptor o2) {
        return comp.compare(o1.getAddress(), o2.getAddress());
    }
}

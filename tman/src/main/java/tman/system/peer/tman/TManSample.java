package tman.system.peer.tman;

import java.util.ArrayList;
import java.util.List;


import se.sics.kompics.Event;
import se.sics.kompics.address.Address;


public class TManSample extends Event {
	List<Address> partners = new ArrayList<Address>();

//-------------------------------------------------------------------
	public TManSample(List<Address> partners) {
		this.partners = partners;
	}
        
	public TManSample() {
	}

//-------------------------------------------------------------------
	public List<Address> getSample() {
		return this.partners;
	}
}

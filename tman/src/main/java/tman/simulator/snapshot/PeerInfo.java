package tman.simulator.snapshot;

import java.util.ArrayList;
import java.util.List;
import se.sics.kompics.address.Address;


public class PeerInfo {
	private List<Address> tmanPartners;
	private List<Address> cyclonPartners;

//-------------------------------------------------------------------
	public PeerInfo() {
		this.tmanPartners = new ArrayList<Address>();
		this.cyclonPartners = new ArrayList<Address>();
	}

//-------------------------------------------------------------------
	public void updateTManPartners(List<Address> partners) {
		this.tmanPartners = partners;
	}

//-------------------------------------------------------------------
	public void updateCyclonPartners(List<Address> partners) {
		this.cyclonPartners = partners;
	}

//-------------------------------------------------------------------
	public List<Address> getTManPartners() {
		return this.tmanPartners;
	}

//-------------------------------------------------------------------
	public List<Address> getCyclonPartners() {
		return this.cyclonPartners;
	}
}

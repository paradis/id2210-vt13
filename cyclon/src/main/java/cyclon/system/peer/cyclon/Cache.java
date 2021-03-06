package cyclon.system.peer.cyclon;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import se.sics.kompics.address.Address;

public class Cache {
	private Comparator<ViewEntry> comparatorByAge = new Comparator<ViewEntry>() {
		public int compare(ViewEntry o1, ViewEntry o2) {
			if (o1.getDescriptor().getAge() > o2.getDescriptor().getAge()) {
				return 1;
			} else if (o1.getDescriptor().getAge() < o2.getDescriptor().getAge()) {
				return -1;
			} else {
				return 0;
			}
		}
	};

//-------------------------------------------------------------------
	private final int size;
	private final Address self;
	private ArrayList<ViewEntry> entries;
	private HashMap<Address, ViewEntry> d2e;
	private Random random = new Random(10);

//-------------------------------------------------------------------
	public Cache(int size, Address self) {
		super();
		this.self = self;
		this.size = size;
		this.entries = new ArrayList<ViewEntry>();
		this.d2e = new HashMap<Address, ViewEntry>();
	}

//-------------------------------------------------------------------
	public void incrementDescriptorAges() {
		for (ViewEntry entry : entries) {
			entry.getDescriptor().incrementAndGetAge();
		}
	}

//-------------------------------------------------------------------
	public Address selectPeerToShuffleWith() {
		if (entries.isEmpty()) {
			return null;
		}
		ViewEntry oldestEntry = Collections.max(entries, comparatorByAge);
		removeEntry(oldestEntry);
		return oldestEntry.getDescriptor().getAddress();
	}

//-------------------------------------------------------------------
	public ArrayList<PeerDescriptor> selectToSendAtActive(int count, Address destinationPeer) {
		ArrayList<ViewEntry> randomEntries = generateRandomSample(count);

		ArrayList<PeerDescriptor> descriptors = new ArrayList<PeerDescriptor>();
		for (ViewEntry cacheEntry : randomEntries) {
			cacheEntry.sentTo(destinationPeer);
			descriptors.add(cacheEntry.getDescriptor());
		}
		
		return descriptors;
	}

//-------------------------------------------------------------------
	public ArrayList<PeerDescriptor> selectToSendAtPassive(int count, Address destinationPeer) {
		ArrayList<ViewEntry> randomEntries = generateRandomSample(count);
		ArrayList<PeerDescriptor> descriptors = new ArrayList<PeerDescriptor>();
		
		for (ViewEntry cacheEntry : randomEntries) {
			cacheEntry.sentTo(destinationPeer);
			descriptors.add(cacheEntry.getDescriptor());
		}
		
		return descriptors;
	}

//-------------------------------------------------------------------
	public void selectToKeep(Address from, ArrayList<PeerDescriptor> descriptors) {
		LinkedList<ViewEntry> entriesSentToThisPeer = new LinkedList<ViewEntry>();
		for (ViewEntry cacheEntry : entries) {
			if (cacheEntry.wasSentTo(from)) {
				entriesSentToThisPeer.add(cacheEntry);
			}
		}

		for (PeerDescriptor descriptor : descriptors) {
			if (self.equals(descriptor.getAddress())) {
				// do not keep descriptor of self
				continue;
			}

			if (d2e.containsKey(descriptor.getAddress())) {
				// we already have an entry for this peer. keep the youngest one
				ViewEntry entry = d2e.get(descriptor.getAddress());
				if (entry.getDescriptor().getAge() > descriptor.getAge()) {
					// we keep the lowest age descriptor
					removeEntry(entry);
					addEntry(new ViewEntry(descriptor));
					continue;
				} else {
					continue;
				}
			}
			
			if (entries.size() < size) {
				// fill an empty slot
				addEntry(new ViewEntry(descriptor));
				continue;
			}
			
			// replace one slot out of those sent to this peer
			ViewEntry sentEntry = entriesSentToThisPeer.poll();
			if (sentEntry != null) {
				removeEntry(sentEntry);
				addEntry(new ViewEntry(descriptor));
			}
		}
	}

//-------------------------------------------------------------------
	public final ArrayList<PeerDescriptor> getAll() {
		ArrayList<PeerDescriptor> descriptors = new ArrayList<PeerDescriptor>();

		for (ViewEntry cacheEntry : entries)
			descriptors.add(cacheEntry.getDescriptor());
		
		return descriptors;
	}

//-------------------------------------------------------------------
	public final List<Address> getRandomPeers(int count) {
		ArrayList<ViewEntry> randomEntries = generateRandomSample(count);
		LinkedList<Address> randomPeers = new LinkedList<Address>();

		for (ViewEntry cacheEntry : randomEntries) {
			randomPeers.add(cacheEntry.getDescriptor().getAddress());
		}

		return randomPeers;
	}

//-------------------------------------------------------------------
	private final ArrayList<ViewEntry> generateRandomSample(int n) {
		ArrayList<ViewEntry> randomEntries;
		if (n >= entries.size()) {
			// return all entries
			randomEntries = new ArrayList<ViewEntry>(entries);
		} else {
			// return count random entries
			randomEntries = new ArrayList<ViewEntry>();
			// Don Knuth, The Art of Computer Programming, Algorithm S(3.4.2)
			int t = 0, m = 0, N = entries.size();
			while (m < n) {
				int x = random.nextInt(N - t);
				if (x < n - m) {
					randomEntries.add(entries.get(t));
					m += 1;
					t += 1;
				} else {
					t += 1;
				}
			}
		}
		return randomEntries;
	}

//-------------------------------------------------------------------
	private void addEntry(ViewEntry entry) {
		entries.add(entry);
		d2e.put(entry.getDescriptor().getAddress(), entry);
		checkSize();
	}

//-------------------------------------------------------------------
	private void removeEntry(ViewEntry entry) {
		entries.remove(entry);
		d2e.remove(entry.getDescriptor().getAddress());
		checkSize();
	}

//-------------------------------------------------------------------
	private void checkSize() {
		if (entries.size() != d2e.size())
			throw new RuntimeException("WHD " + entries.size() + " <> " + d2e.size());
	}
}

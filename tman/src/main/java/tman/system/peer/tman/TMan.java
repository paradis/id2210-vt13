package tman.system.peer.tman;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;

import tman.simulator.snapshot.Snapshot;
import common.configuration.TManConfiguration;
import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;

/*
 * Implement the gradient by keeping a list of peers which are the best according to ourself:
 * That is to say, Peers who are closest to me, preferring those with a larger id.
 * The list is refreshed with the cyclonsamples
 */
public final class TMan extends ComponentDefinition {
    private static final Logger logger = LoggerFactory.getLogger(TMan.class);

    Negative<TManSamplePort> tmanPort = negative(TManSamplePort.class);
    Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);

    private Address self;
    private TManConfiguration tmanConfiguration;
    private Random r;
    
    private List<PeerDescriptor> tmanPartners;
    private List<Address> cyclonPartners;

//------------------------------------------------------------------------------
//                 Initialisation
//------------------------------------------------------------------------------
    
    public class TManSchedule extends Timeout {

        public TManSchedule(SchedulePeriodicTimeout request) {
            super(request);
        }
        public TManSchedule(ScheduleTimeout request) {
            super(request);
        }
    }
    	
    public TMan() {
        tmanPartners = new ArrayList<PeerDescriptor>();
        cyclonPartners = new ArrayList<Address>();

        subscribe(handleInit, control);
        subscribe(handleRound, timerPort);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleTManPartnersResponse, networkPort);
        subscribe(handleTManPartnersRequest, networkPort);
    }

    Handler<TManInit> handleInit = new Handler<TManInit>() {
        @Override
        public void handle(TManInit init) {
            self = init.getSelf();
            tmanConfiguration = init.getConfiguration();
            
            long period = tmanConfiguration.getPeriod();
            r = new Random(tmanConfiguration.getSeed());
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
            rst.setTimeoutEvent(new TManSchedule(rst));
            trigger(rst, timerPort);

        }
    };

//------------------------------------------------------------------------------
//                 Send our results to our peer
//------------------------------------------------------------------------------
    
    /*
     * Periodically called to give TManSample to our peer.
     */
    Handler<TManSchedule> handleRound = new Handler<TManSchedule>() {
        @Override
        public void handle(TManSchedule event) {
            Snapshot.updateTManPartners(self, getTmanAddress());
            logger.debug("TManSchedule: " + self.getId() + " tmanPeers=" + printAdresses(tmanPartners));

            // Publish sample to connected components
            trigger(new TManSample(getTmanAddress()), tmanPort);            
        }
    };
    
    /*
     * Return a list of address from a list of peers descriptors.
     */
    List<Address> getTmanAddress()
    {
        List<Address> res = new ArrayList<Address>();
        for (PeerDescriptor d : tmanPartners) {
            res.add(d.getAddress());
        }
        return res;
    }

//------------------------------------------------------------------------------
//            Receive fresh peers and construct our TManSample
//  Cycle:
//  1. handleCyclonSample
//  2. handleTManPartnersRequest (in another peer)
//  3. handleTManPartnersResponse
//  
//  There are some useful functions to do it:
//  * selectPeer: select a peer for exchange info
//  * merge: merge list of peerDescriptors
//  * selectView: clean temporary lists before storing it
//------------------------------------------------------------------------------
    
    //--------------------------------------------------------------------------
    // Step 1
    
    /*
     * Receive fresh peer from cyclon
     */
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            
            ///
            // Select only the peers in our partition
            ///
            int myPartition = self.getId() % tmanConfiguration.getNumPartitions();
            
            cyclonPartners.clear();
            for (Address a : event.getSample())
                if (myPartition == a.getId() % tmanConfiguration.getNumPartitions())
                    cyclonPartners.add(a);

            ///
            // With these new peers, contact a selected peer and exchange TMan Informations
            ///
            Address dest = selectPeer();
            if (dest == null)
                logger.info("Request: " + self.getId() + " Unable to find a peer for the moment");
            else
            {
                List<PeerDescriptor> buf = new ArrayList<PeerDescriptor>(tmanPartners);
                merge(buf, new PeerDescriptor(self));
                
                // merge with the cyclonPartners
                for (Address a : cyclonPartners)
                    merge(buf, new PeerDescriptor(a)); 

                logger.debug("Request: " + self.getId() + " -> " + dest.getId() + ": " + buf.size() + " peers " + printAdresses(buf));
                trigger(new ExchangeMsg.Request(self, dest, buf), networkPort);
            }                
            
        }
    };
    
    //--------------------------------------------------------------------------
    // Step 2:
    
    /*
     * Receive a TMan sample from another peer, return to peer my own list and merge it with my list 
     */
    Handler<ExchangeMsg.Request> handleTManPartnersRequest = new Handler<ExchangeMsg.Request>() {
        @Override
        public void handle(ExchangeMsg.Request event) {
            // Our list of peers
            List<PeerDescriptor> buf = new ArrayList<PeerDescriptor>(tmanPartners);
            merge(buf, new PeerDescriptor(self));
            
            // merge with the cyclonPartners received in the last handleCyclonSample()
            if (cyclonPartners != null)
                for (Address a : cyclonPartners)
                    merge(buf, new PeerDescriptor(a)); 
            
            logger.debug("Response: " + self.getId() + " -> " + event.getSource().getId() + ": " + buf.size() + " peers " + printAdresses(buf));
            trigger(new ExchangeMsg.Response(self, event.getSource(), buf), networkPort);
            
            // Merge
            merge(buf, event.getBuffer());
            
            // Clean our buffer before storing it
            tmanPartners = selectView(buf);               
        }
    };
    
    //--------------------------------------------------------------------------
    // Step 3:
    
    /*
     * Receive a list from another peer, merge it with my list
     */
    Handler<ExchangeMsg.Response> handleTManPartnersResponse = new Handler<ExchangeMsg.Response>() {
        @Override
        public void handle(ExchangeMsg.Response event) {
            List<PeerDescriptor> buf = new ArrayList<PeerDescriptor>(tmanPartners);
            
            merge(buf, new PeerDescriptor(event.getSource()));
            merge(buf, event.getBuffer());
            
            // Clean our buffer before storing it
            tmanPartners = selectView(buf);
        }
    };
    
    //--------------------------------------------------------------------------
    // Select the peer which will be use to exchange TManPartners
    
    /*
     * Select the better peer with sometimes a little randomness (by using getSoftMaxAddress)
     * If TManParters is empty, fallback with cyclonPartners
     */
    Address selectPeer() {       
        if (tmanPartners.size() > 0)
            // We don't use the age: so if a great peer is suppose to be removed, we will try multiple times to contact it
            return getSoftMaxAddress(tmanPartners, new ComparatorPeerById(self)).getAddress();
        // First run
        else if (cyclonPartners.size() > 0)
            return getSoftMaxAddress(cyclonPartners, new ComparatorById(self));
        else
            return null;
    }
    
    /*
     * if you call this method with a list of entries, it will
     * return a single node, weighted towards the 'best' node (as defined by
     * ComparatorById) with the temperature controlling the weighting.
     * A temperature of '1.0' will be greedy and always return the best node.
     * A temperature of '0.000001' will return a random node.
     * A temperature of '0.0' will throw a divide by zero exception :)
     * Reference:
     * http://webdocs.cs.ualberta.ca/~sutton/book/2/node4.html
     */
    public <T> T getSoftMaxAddress(List<T> entries, Comparator<T> comp) {
        Collections.sort(entries, comp);

        double rnd = r.nextDouble();
        double total = 0.0d;
        double[] values = new double[entries.size()];
        int j = entries.size() + 1;
        for (int i = 0; i < entries.size(); i++) {
            // get inverse of values - lowest have highest value.
            double val = j;
            j--;
            values[i] = Math.exp(val / tmanConfiguration.getTemperature());
            total += values[i];
        }

        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                values[i] += values[i - 1];
            }
            // normalise the probability for this entry
            double normalisedUtility = values[i] / total;
            if (normalisedUtility >= rnd) {
                return entries.get(i);
            }
        }
        return entries.get(entries.size() - 1);
    }
    
    //--------------------------------------------------------------------------
    // Merge peers descriptors 
    
    /*
     * Merge a list of peer descriptor with a peer descriptor:
     * We keep the freshest if there are duplicate
     */
    public static List<PeerDescriptor> merge(List<PeerDescriptor> list, PeerDescriptor descriptor) {
        ListIterator<PeerDescriptor> i = list.listIterator();
        while (i.hasNext()) {
            PeerDescriptor d = i.next();
            if (d.getAddress() == descriptor.getAddress())
            {
                if (d.getAge() > descriptor.getAge())
                    i.set(descriptor);

                return list;
            }
        }
        list.add(descriptor);
        return list;
    }
    
    /*
     * Merge two lists of peers descriptors
     */
    public static List<PeerDescriptor> merge(List<PeerDescriptor> buffer, List<PeerDescriptor> descriptors) {
        for (PeerDescriptor d : descriptors)
            merge(buffer, d);
        
        return buffer;
    }
    
    //--------------------------------------------------------------------------
    // After merging peers descriptors, update, clean and truncate the list
    
    /*
     * Keep the useful information in our list of TManPartners
     */
    private List<PeerDescriptor> selectView(List<PeerDescriptor> list) {
        
        //purge old entries and increment ages
        Iterator<PeerDescriptor> i = list.iterator();
        while (i.hasNext()) {
            PeerDescriptor d = i.next();
            if (d.incrementAndGetAge() > tmanConfiguration.getMaxAge())
                i.remove();
        }
        
        // sort peers by id (closer to us, higher if possible) to keep only best peers
        Collections.sort(list, new ComparatorPeerById(self));
        
        // We remove ourself from the list if needed
        //since we have just sorted the list, we will be in the first position
        if (list.get(0).getAddress() == self)
            list.remove(0);

        // Trucate the list if necessary
        if (list.size() < tmanConfiguration.getSampleSize())
            return list;
        
        return list.subList(0, tmanConfiguration.getSampleSize());
    }

    ///
    // For debug purpose
    ///
    String printAdresses(List<PeerDescriptor> list)
    {
        String str = "[";
        for (PeerDescriptor d : list)
            str += "{" + d.getAddress().getId() + ", " + d.getAge() + "} ";
        return str + "]";
    }
}

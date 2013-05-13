package tman.system.peer.tman;

import common.configuration.TManConfiguration;
import java.util.ArrayList;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;
import java.util.Collections;
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

public final class TMan extends ComponentDefinition {
    private static final Logger logger = LoggerFactory.getLogger(TMan.class);

    Negative<TManSamplePort> tmanPort = negative(TManSamplePort.class);
    Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);
    private long period;
    private Address self;
    private List<PeerDescriptor> tmanPartners;
    private List<Address> cyclonPartners;
    private TManConfiguration tmanConfiguration;
    private Random r;

    public class TManSchedule extends Timeout {

        public TManSchedule(SchedulePeriodicTimeout request) {
            super(request);
        }
        public TManSchedule(ScheduleTimeout request) {
            super(request);
        }
    }
    
//-------------------------------------------------------------------	
    public TMan() {
        tmanPartners = new ArrayList<PeerDescriptor>();

        subscribe(handleInit, control);
        subscribe(handleRound, timerPort);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleTManPartnersResponse, networkPort);
        subscribe(handleTManPartnersRequest, networkPort);
    }
//-------------------------------------------------------------------	
    Handler<TManInit> handleInit = new Handler<TManInit>() {
        @Override
        public void handle(TManInit init) {
            self = init.getSelf();
            tmanConfiguration = init.getConfiguration();
            period = tmanConfiguration.getPeriod();
            r = new Random(tmanConfiguration.getSeed());
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
            rst.setTimeoutEvent(new TManSchedule(rst));
            trigger(rst, timerPort);

        }
    };
//-------------------------------------------------------------------	
    Handler<TManSchedule> handleRound = new Handler<TManSchedule>() {
        @Override
        public void handle(TManSchedule event) {
            Snapshot.updateTManPartners(self, getTmanAddress());

            // Publish sample to connected components
            trigger(new TManSample(getTmanAddress()), tmanPort);            
        }
    };
    
    List<Address> getTmanAddress()
    {
        List<Address> res = new ArrayList<Address>();
        for (PeerDescriptor d : tmanPartners) {
            res.add(d.getAddress());
        }
        return res;
    }
//-------------------------------------------------------------------	
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            cyclonPartners = event.getSample();

            // Another list of peerss
            Address dest = selectPeer();
            if (dest == null)
                logger.error("Tman (request) " + self + " Unable to find a peer");
            else
            {
                List<PeerDescriptor> buf = new ArrayList<PeerDescriptor>(tmanPartners);
                merge(buf, new PeerDescriptor(self));
                
                // merge with the cyclonPartners
                for (Address a : cyclonPartners)
                    merge(buf, new PeerDescriptor(a)); 

                logger.debug("Tman (request) " + self + " -> " + dest + ": " + buf.size() + " peers");
                trigger(new ExchangeMsg.Request(self, dest, buf), networkPort);
            }                
            
        }
    };
    
    /*
     * Select the better peer with sometimes a little randomness (getSoftMaxAddress)
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
    
//-------------------------------------------------------------------	
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
            
            logger.debug("Tman (answer)" + self + " -> " + event.getSource() + ": " + buf.size() + " peers");
            trigger(new ExchangeMsg.Response(self, event.getSource(), buf), networkPort);
            
            // Merge
            buf.addAll(event.getBuffer());
            tmanPartners = selectView(buf);               
        }
    };
    
    Handler<ExchangeMsg.Response> handleTManPartnersResponse = new Handler<ExchangeMsg.Response>() {
        @Override
        public void handle(ExchangeMsg.Response event) {
            List<PeerDescriptor> buf = new ArrayList<PeerDescriptor>(tmanPartners);
            merge(buf, new PeerDescriptor(event.getSource()));
            merge(buf, event.getBuffer());
            tmanPartners = selectView(buf);
        }
    };
    
    private List<PeerDescriptor> merge(List<PeerDescriptor> list, PeerDescriptor descriptor) {
        Iterator<PeerDescriptor> i = list.iterator();
        while (i.hasNext()) {
            PeerDescriptor d = i.next();
            //TODO à verifier que c'est pas seulement les refs
            if (d.getAddress() == descriptor.getAddress())
            {
                if (d.getAge() > descriptor.getAge())
                {
                    // TODO: moche
                    i.remove(); 
                    list.add(descriptor);
                }
                return list;
            }
        }
        list.add(descriptor);
        return list;
    }
    
    private List<PeerDescriptor> merge(List<PeerDescriptor> buffer, List<PeerDescriptor> descriptors) {
        for (PeerDescriptor d : descriptors)
            merge(buffer, d);
        
        return buffer;
    }
    
    private List<PeerDescriptor> selectView(List<PeerDescriptor> list) {
        
        //purge old entries and increment ages
        Iterator<PeerDescriptor> i = list.iterator();
        while (i.hasNext()) {
            PeerDescriptor d = i.next();
            if (d.incrementAndGetAge() > tmanConfiguration.getMaxAge())
            {
                logger.info(self + " Remove old entry: " + d);
                i.remove();
            }
        }
        
        Collections.sort(list, new ComparatorPeerById(self));
        
        if (list.size() < tmanConfiguration.getSampleSize())
            return list;
        
        return list.subList(0, tmanConfiguration.getSampleSize());
    }

        // TODO - if you call this method with a list of entries, it will
    // return a single node, weighted towards the 'best' node (as defined by
    // ComparatorById) with the temperature controlling the weighting.
    // A temperature of '1.0' will be greedy and always return the best node.
    // A temperature of '0.000001' will return a random node.
    // A temperature of '0.0' will throw a divide by zero exception :)
    // Reference:
    // http://webdocs.cs.ualberta.ca/~sutton/book/2/node4.html
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
        
}

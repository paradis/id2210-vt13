package tman.system.peer.tman;

import common.configuration.TManConfiguration;
import java.util.ArrayList;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    private ArrayList<Address> tmanPartners;
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
        tmanPartners = new ArrayList<Address>();

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
            Snapshot.updateTManPartners(self, tmanPartners);

            // Publish sample to connected components
            trigger(new TManSample(tmanPartners), tmanPort);            
        }
    };
//-------------------------------------------------------------------	
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            cyclonPartners = event.getSample();

            // Another list of peerss
            Address dest = selectPeer();
            if (dest != null)
            {
                HashSet<Address> buf = new HashSet<Address>(tmanPartners);
                buf.add(self);
                buf.addAll(cyclonPartners);

                logger.debug("Tman (request) " + self + " -> " + dest + ": " + buf.size() + " peers");
                trigger(new ExchangeMsg.Request(self, dest, buf), networkPort);
            }
            else
                logger.error("Tman (request) " + self + " Unable to find a peer");
            
        }
    };
    
    Address selectPeer() {
        if (tmanPartners.size() > 0)
            return getSoftMaxAddress(tmanPartners);
        // First run
        else if (cyclonPartners.size() > 0)
            return getSoftMaxAddress(cyclonPartners);
        else
            return null;
    }
    
//-------------------------------------------------------------------	
    Handler<ExchangeMsg.Request> handleTManPartnersRequest = new Handler<ExchangeMsg.Request>() {
        @Override
        public void handle(ExchangeMsg.Request event) {
            // Our list of peers
            HashSet<Address> buf = new HashSet<Address>(tmanPartners);
            buf.add(self);
            buf.addAll(cyclonPartners); //received in the last handleCyclonSample()
            
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
            HashSet<Address> buf = new HashSet<Address>(tmanPartners);
            buf.addAll(event.getBuffer());
            tmanPartners = selectView(buf);
        }
    };
    
    private ArrayList<Address> selectView(HashSet<Address> buffer) {
        List<Address> tempView = new ArrayList<Address>(buffer);
        // Take the age into account to remove obsolete peers
        Collections.sort(tempView, new ComparatorById(self));
        
        if (tempView.size() < tmanConfiguration.getSampleSize())
            return (ArrayList<Address>) tempView;
        
        return (ArrayList<Address>) tempView.subList(0, tmanConfiguration.getSampleSize());
    }

        // TODO - if you call this method with a list of entries, it will
    // return a single node, weighted towards the 'best' node (as defined by
    // ComparatorById) with the temperature controlling the weighting.
    // A temperature of '1.0' will be greedy and always return the best node.
    // A temperature of '0.000001' will return a random node.
    // A temperature of '0.0' will throw a divide by zero exception :)
    // Reference:
    // http://webdocs.cs.ualberta.ca/~sutton/book/2/node4.html
    public Address getSoftMaxAddress(List<Address> entries) {
        Collections.sort(entries, new ComparatorById(self));

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

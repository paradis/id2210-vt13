package common.configuration;

import common.simulation.scenarios.Scenario;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

public final class SearchConfiguration {

    // The period between each request for new entries
    private final int updatePeriod;
    // The Timeout before returning the results of a user web research
    private final int lookupTimeout;
    // Timeout before retrying when we have not receive a id from the leader when adding a new entry
    private final int idRequestTimeout;
    // Time for the leader to receive a MaxIdRequest or collect entries when the previous leader is down
    private final int leaderWarmUpTime;
    
    private final int numPartitions;
    // max nodes per partition in routing table
    private final int maxNumRoutingEntries;
    // The ratio between Neighbors and Tman when selecting the peer we will use to search for new entries
    private final double ratioNeighborsTMan;
    
    private final long seed;
   
    public SearchConfiguration(int updatePeriod, int lookupTimeout, int idRequestTimeout, int leaderWarmUpTime, int numPartitions, int maxNumRoutingEntries, double ratioNeighborsTMan, long seed) {
        this.updatePeriod = updatePeriod;
        this.lookupTimeout = lookupTimeout;
        this.idRequestTimeout = idRequestTimeout;
        this.leaderWarmUpTime = leaderWarmUpTime;
        
        this.numPartitions = numPartitions;
        this.maxNumRoutingEntries = maxNumRoutingEntries;
        this.ratioNeighborsTMan = ratioNeighborsTMan;
        
        this.seed = seed;
    }

    public int getUpdatePeriod() {
        return this.updatePeriod;
    }
    
    public int getLookupTimeout() {
        return lookupTimeout;
    }
    
    public int getIdRequestTimeout() {
        return idRequestTimeout;
    }
    
    public int getLeaderWarmUpTime() {
        return leaderWarmUpTime;
    }
    
    public int getNumPartitions() {
        return numPartitions;
    }

    public int getMaxNumRoutingEntries() {
        return maxNumRoutingEntries;
    }
    
    public double getRatioNeighborsTMan() {
        return ratioNeighborsTMan;
    }

    public long getSeed() {
        return seed;
    }

    
    public void store(String file) throws IOException {
        Properties p = new Properties();
        p.setProperty("updatePeriod", "" + updatePeriod);
        p.setProperty("lookupTimeout", "" + lookupTimeout);
        p.setProperty("idRequestTimeout", "" + idRequestTimeout);
        p.setProperty("leaderWarmUpTime", "" + leaderWarmUpTime);
        
        p.setProperty("numPartitions", "" + numPartitions);
        p.setProperty("maxNumRoutingEntries", "" + maxNumRoutingEntries);
        p.setProperty("ratioNeighborsTMan", "" + ratioNeighborsTMan);
        
        p.setProperty("seed", "" + seed);


        Writer writer = new FileWriter(file);
        p.store(writer, "se.sics.kompics.p2p.overlay.application");
    }

    public static SearchConfiguration load(String file) throws IOException {
        Properties p = new Properties();
        Reader reader = new FileReader(file);
        p.load(reader);

        int updatePeriod = Integer.parseInt(p.getProperty("updatePeriod"));
        int lookupTimeout = Integer.parseInt(p.getProperty("lookupTimeout"));
        int idRequestTimeout = Integer.parseInt(p.getProperty("idRequestTimeout"));
        int leaderWarmUpTime = Integer.parseInt(p.getProperty("leaderWarmUpTime"));
        
        int numPartitions = Integer.parseInt(p.getProperty("numPartitions"));
        int maxNumRoutingEntries = Integer.parseInt(p.getProperty("maxNumRoutingEntries"));
        double ratioNeighborsTMan = Double.parseDouble(p.getProperty("ratioNeighborsTMan"));
        
        long seed = Long.parseLong(p.getProperty("seed"));

        return new SearchConfiguration(updatePeriod, lookupTimeout, idRequestTimeout, leaderWarmUpTime, numPartitions, maxNumRoutingEntries, ratioNeighborsTMan, seed);
    }

}

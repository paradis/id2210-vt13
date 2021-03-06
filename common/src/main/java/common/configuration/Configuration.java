package common.configuration;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import se.sics.kompics.address.Address;
import se.sics.kompics.p2p.bootstrap.BootstrapConfiguration;

public class Configuration {

    public static int SNAPSHOT_PERIOD = 5000;
    public static int AVAILABLE_TOPICS = 20;
    public InetAddress ip = null;

    {
        try {
            ip = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
        }
    }
    int webPort = 8080;
    int bootId = Integer.MAX_VALUE;
    int networkPort = 8081;
    Address bootServerAddress = new Address(ip, networkPort, bootId);
    final long seed;
    BootstrapConfiguration bootConfiguration = new BootstrapConfiguration(bootServerAddress, 60000, 4000, 3, 30000, webPort, webPort);
    CyclonConfiguration cyclonConfiguration;
    TManConfiguration tmanConfiguration;
    SearchConfiguration searchConfiguration;

    public Configuration(long seed) throws IOException {
        this.seed = seed;
        
        int numPartition = 1;
        
        searchConfiguration = new SearchConfiguration(1000, // The period between each request for new entries
                                                      1000, // The Timeout before returning the results of a user web research
                                                      2000, // Timeout before retrying when we have not receive a id from the leader when adding a new entry
                                                      5000, // Time for the leader to receive a MaxIdRequest or collect entries when the previous leader is down
                                                      numPartition,
                                                      20,   // nodes per partition in routing table
                                                      0.75, // The ratio between Neighbors and Tman when selecting the peer we will use to search for new entries
                                                      seed);
        tmanConfiguration = new TManConfiguration(seed,
                                                  1000, //peridod
                                                  numPartition,
                                                  0.8, // temperature
                                                  10,  // sampleSize
                                                  10); // maxAge
        cyclonConfiguration = new CyclonConfiguration(seed, 5, 10, 1000, 500000, (long) (Integer.MAX_VALUE - Integer.MIN_VALUE), 20);

        String c = File.createTempFile("bootstrap.", ".conf").getAbsolutePath();
        bootConfiguration.store(c);
        System.setProperty("bootstrap.configuration", c);

        c = File.createTempFile("cyclon.", ".conf").getAbsolutePath();
        cyclonConfiguration.store(c);
        System.setProperty("cyclon.configuration", c);

        c = File.createTempFile("tman.", ".conf").getAbsolutePath();
        tmanConfiguration.store(c);
        System.setProperty("tman.configuration", c);

        c = File.createTempFile("search.", ".conf").getAbsolutePath();
        searchConfiguration.store(c);
        System.setProperty("search.configuration", c);
    }
}

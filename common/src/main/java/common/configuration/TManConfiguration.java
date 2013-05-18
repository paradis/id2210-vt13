package common.configuration;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

public final class TManConfiguration {

    private final long period;
    private final long seed;
    private final int numPartitions;
    private final double temperature;
    private final int sampleSize;
    private final int maxAge;

//-------------------------------------------------------------------
    public TManConfiguration(long seed, long period, int numPartitions, double temperature, int sampleSize, int maxAge) {
        super();
        this.seed = seed;
        this.period = period;
        this.numPartitions = numPartitions;
        this.temperature = temperature;
        this.sampleSize = sampleSize;
        this.maxAge = maxAge;
    }

    public long getSeed() {
        return seed;
    }

//-------------------------------------------------------------------
    public long getPeriod() {
        return this.period;
    }

    //-------------------------------------------------------------------
    public int getNumPartitions() {
        return numPartitions;
    }
    
    //-------------------------------------------------------------------
    public double getTemperature() {
        return temperature;
    }
    
    //-------------------------------------------------------------------
    public int getSampleSize() {
        return sampleSize;
    }
        
    //-------------------------------------------------------------------
    public int getMaxAge() {
        return maxAge;
    }
    
//-------------------------------------------------------------------
    public void store(String file) throws IOException {
        Properties p = new Properties();
        p.setProperty("seed", "" + seed);
        p.setProperty("period", "" + period);
        p.setProperty("numPartitions", "" + numPartitions);
        p.setProperty("temperature", "" + temperature);
        p.setProperty("sampleSize", "" + sampleSize);
        p.setProperty("maxAge", "" + maxAge);

        Writer writer = new FileWriter(file);
        p.store(writer, "se.sics.kompics.p2p.overlay.application");
    }

//-------------------------------------------------------------------
    public static TManConfiguration load(String file) throws IOException {
        Properties p = new Properties();
        Reader reader = new FileReader(file);
        p.load(reader);

        long seed = Long.parseLong(p.getProperty("seed"));
        long period = Long.parseLong(p.getProperty("period"));
        int numPartitions = Integer.parseInt(p.getProperty("numPartitions"));
        double temp = Double.parseDouble(p.getProperty("temperature"));
        int size = Integer.parseInt(p.getProperty("sampleSize"));
        int maxAge = Integer.parseInt(p.getProperty("maxAge"));

        return new TManConfiguration(seed, period, numPartitions, temp, size, maxAge);
    }
}

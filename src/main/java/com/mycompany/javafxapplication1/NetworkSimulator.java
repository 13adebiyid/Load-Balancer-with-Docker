package com.mycompany.javafxapplication1;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Simulates network conditions and delays.
 */
public class NetworkSimulator {
    private static final Logger logger = Logger.getLogger(NetworkSimulator.class.getName());
    private static final Random random = new Random();
    
    /**
     * levels of network traffic
     */
    public enum TrafficLevel {
        LOW(30, 40),    // Light traffic: 30-40 seconds
        MEDIUM(50, 70), // Medium traffic: 50-70 seconds
        HIGH(80, 90);   // Heavy traffic: 80-90 seconds
        
        private final int minDelay;
        private final int maxDelay;
        
        TrafficLevel(int minDelay, int maxDelay) {
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }
        
        public int getMinDelay() { return minDelay; }
        public int getMaxDelay() { return maxDelay; }
    }
    
    private static TrafficLevel currentTrafficLevel = TrafficLevel.LOW;
    
    /**
     * Sets current traffic level 
     */
    public static void setTrafficLevel(TrafficLevel level) {
        currentTrafficLevel = level;
        logger.info("Traffic level set to: " + level + " (Delay range: " + level.getMinDelay() + "-" + level.getMaxDelay() + " seconds)");
    }
    
    /**
     * Gets current traffic level
     */
    public static TrafficLevel getCurrentTrafficLevel() {
        return currentTrafficLevel;
    }
    
    /**
     * Simulates network delay based on current traffic
     */
    public static void simulateNetworkDelay(String operationType) throws InterruptedException {
        int delaySeconds = random.nextInt(currentTrafficLevel.getMaxDelay() - currentTrafficLevel.getMinDelay() + 1) + currentTrafficLevel.getMinDelay();
        
        logger.info(String.format("Simulating network delay for %s: %d seconds (Traffic Level: %s)",
                operationType, delaySeconds, currentTrafficLevel));
        
        TimeUnit.SECONDS.sleep(delaySeconds);
    }
    
    /**
     * Simulates network delay with progress update
     */
    public static void simulateNetworkDelayWithProgress(
            String operationType, 
            ProgressCallback progressCallback) throws InterruptedException {
            
        int delaySeconds = random.nextInt(currentTrafficLevel.getMaxDelay() - currentTrafficLevel.getMinDelay() + 1) + currentTrafficLevel.getMinDelay();
        
        logger.info(String.format("Simulating network delay for %s: %d seconds (Traffic Level: %s)",operationType, delaySeconds, currentTrafficLevel));
        
        long totalMillis = delaySeconds * 1000L;
        long updateInterval = 100; // Update progress every 100ms
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < totalMillis) {
            Thread.sleep(updateInterval);
            double progress = (double)(System.currentTimeMillis() - startTime) / totalMillis;
            progressCallback.onProgress(Math.min(1.0, progress));
        }
        
        progressCallback.onProgress(1.0);
    }
    
    /**
     * interface for progress updates
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(double progress);
    }
}
package com.mycompany.javafxapplication1;

/**
 * Test class for dynamic scaling functionality (not currently working)
 */
public class ScalingTest {
    private LoadBalancer loadBalancer;
    private static final int CONTAINER_INIT_TIMEOUT = 30;
    
    public ScalingTest() {
        this.loadBalancer = new LoadBalancer();
        waitForContainerInitialization();
    }
    
    private void waitForContainerInitialization() {
        System.out.println("Waiting for containers to initialize...");
        int attempts = CONTAINER_INIT_TIMEOUT;
        while (attempts > 0 && loadBalancer.getContainerCount() == 0) {
            try {
                Thread.sleep(1000);
                attempts--;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (loadBalancer.getContainerCount() == 0) {
            System.err.println("Failed to initialize containers after " + CONTAINER_INIT_TIMEOUT + " seconds");
        } else {
            System.out.println("Successfully initialized with " + loadBalancer.getContainerCount() + " containers");
        }
    }
    
    /**
     * Simulates increasing and decreasing system load to test scaling behavior
     */
    public void runScalingTest() {
        System.out.println("\n=== Starting Scaling Test ===");
        
        if (loadBalancer.getContainerCount() == 0) {
            System.err.println("Cannot run test - no containers available");
            return;
        }
        
        try {
            System.out.println("\nPhase 1: Normal Load");
            simulateLoad(50);  // 50% load
            Thread.sleep(10000);  
            
            System.out.println("\nPhase 2: High Load");
            simulateLoad(90);  // 90% load
            Thread.sleep(15000);  
            
            System.out.println("\nPhase 3: Return to Normal Load");
            simulateLoad(50);
            Thread.sleep(10000);
            
            System.out.println("\nPhase 4: Low Load");
            simulateLoad(20);  // 20% load 
            Thread.sleep(15000);
            
        } catch (InterruptedException e) {
            System.err.println("Test interrupted: " + e.getMessage());
        } finally {
            loadBalancer.shutdown();
        }
        
        System.out.println("\n=== Scaling Test Complete ===");
    }
    
    /**
     * Simulates load level by adding connections to containers
     */
    private void simulateLoad(int loadPercentage) {
        int totalContainers = loadBalancer.getContainerCount();
        if (totalContainers == 0) {
            System.out.println("No containers available for load simulation");
            return;
        }
        
        int connectionsPerContainer = (int)((loadPercentage / 100.0) * 10);
        int totalConnections = connectionsPerContainer * totalContainers;
        
        System.out.println(String.format("Simulating %d%% load (%d connections across %d containers)",loadPercentage, totalConnections, totalContainers));
        
        for (FileStorageContainer container : loadBalancer.getContainers()) {
            while (container.getActiveConnections() > 0) {
                container.decrementActiveConnections();
            }
            
            for (int i = 0; i < connectionsPerContainer; i++) {
                container.incrementActiveConnections();
            }
            
            System.out.println(String.format("Container %s: %d active connections",container.getId(), container.getActiveConnections()));
        }
    }
    
    public static void main(String[] args) {
        ScalingTest test = new ScalingTest();
        test.runScalingTest();
    }
}
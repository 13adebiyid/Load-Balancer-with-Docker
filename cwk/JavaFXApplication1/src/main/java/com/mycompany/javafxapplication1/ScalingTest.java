package com.mycompany.javafxapplication1;

/**
 * Test class for demonstrating dynamic scaling functionality
 */
public class ScalingTest {
    private LoadBalancer loadBalancer;
    
    public ScalingTest() {
        // Initialize the load balancer as it would be in production
        this.loadBalancer = new LoadBalancer();
    }
    
    /**
     * Simulates increasing and decreasing system load to test scaling behavior
     */
    public void runScalingTest() {
        System.out.println("\n=== Starting Scaling Test ===");
        
        try {
            // First, simulate normal load to establish baseline
            System.out.println("\nPhase 1: Normal Load");
            simulateLoad(50);  // 50% load
            Thread.sleep(10000);  // Wait for system to stabilize
            
            // Simulate high load to trigger scale up
            System.out.println("\nPhase 2: High Load");
            simulateLoad(90);  // 90% load should trigger scale up
            Thread.sleep(15000);  // Wait for scaling to complete
            
            // Return to normal load
            System.out.println("\nPhase 3: Return to Normal Load");
            simulateLoad(50);
            Thread.sleep(10000);
            
            // Simulate low load to trigger scale down
            System.out.println("\nPhase 4: Low Load");
            simulateLoad(20);  // 20% load should trigger scale down
            Thread.sleep(15000);
            
        } catch (InterruptedException e) {
            System.err.println("Test interrupted: " + e.getMessage());
        } finally {
            // Clean up
            loadBalancer.shutdown();
        }
        
        System.out.println("\n=== Scaling Test Complete ===");
    }
    
    /**
     * Simulates a specific load level by adding connections to containers
     * @param loadPercentage Desired load level (0-100)
     */
    private void simulateLoad(int loadPercentage) {
        // Calculate number of connections needed for desired load
        int totalContainers = loadBalancer.getContainerCount();
        if (totalContainers == 0) {
            System.out.println("No containers available for load simulation");
            return;
        }
        
        // Each container can handle 10 connections at 100% load
        int connectionsPerContainer = (int)((loadPercentage / 100.0) * 10);
        int totalConnections = connectionsPerContainer * totalContainers;
        
        System.out.println(String.format(
            "Simulating %d%% load (%d connections across %d containers)",
            loadPercentage, totalConnections, totalContainers
        ));
        
        // Add the connections to containers
        for (FileStorageContainer container : loadBalancer.getContainers()) {
            // Reset existing connections
            while (container.getActiveConnections() > 0) {
                container.decrementActiveConnections();
            }
            
            // Add new connections for desired load
            for (int i = 0; i < connectionsPerContainer; i++) {
                container.incrementActiveConnections();
            }
            
            System.out.println(String.format(
                "Container %s: %d active connections",
                container.getId(), container.getActiveConnections()
            ));
        }
    }
    
    public static void main(String[] args) {
        ScalingTest test = new ScalingTest();
        test.runScalingTest();
    }
}
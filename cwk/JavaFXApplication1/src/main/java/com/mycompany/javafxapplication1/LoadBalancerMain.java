package com.mycompany.javafxapplication1;

/**
 * Main entry point for the load balancer container
 * Starts the load balancer service and network server
 */
public class LoadBalancerMain {
    public static void main(String[] args) {
        System.out.println("Starting Load Balancer service...");
        
        // Create and initialize the load balancer
        LoadBalancer loadBalancer = new LoadBalancer();
        
        // Add initial storage containers
        for (int i = 1; i <= 4; i++) {
            FileStorageContainer container = new FileStorageContainer(
                "container-" + i,
                "/storage/container" + i
            );
            loadBalancer.addContainer(container);
        }
        
        // Start the network server (default port 8080)
        LoadBalancerServer server = new LoadBalancerServer(8080, loadBalancer);
        server.start();
        
        System.out.println("Load Balancer service is running");
    }
}
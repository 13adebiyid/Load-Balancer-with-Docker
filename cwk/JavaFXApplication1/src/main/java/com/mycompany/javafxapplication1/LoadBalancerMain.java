package com.mycompany.javafxapplication1;

/**
 * Main entry point for the load balancer container
 * Starts the load balancer service and network server
 */
public class LoadBalancerMain {
    public static void main(String[] args) {
        System.out.println("Starting Load Balancer service...");
        
        LoadBalancer loadBalancer = new LoadBalancer();
        
        // Add containers using Docker container paths
        loadBalancer.addContainer(new FileStorageContainer(
            "container-1", "/storage/container1"));
        loadBalancer.addContainer(new FileStorageContainer(
            "container-2", "/storage/container2"));
        loadBalancer.addContainer(new FileStorageContainer(
            "container-3", "/storage/container3"));
        loadBalancer.addContainer(new FileStorageContainer(
            "container-4", "/storage/container4"));
        
        // Start network server
        LoadBalancerServer server = new LoadBalancerServer(8080, loadBalancer);
        server.start();
        
        System.out.println("Load Balancer service is running");
    }
}
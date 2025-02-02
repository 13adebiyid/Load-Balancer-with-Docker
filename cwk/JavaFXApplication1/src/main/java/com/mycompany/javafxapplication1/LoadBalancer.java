package com.mycompany.javafxapplication1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * LoadBalancer class that distributes work across multiple file storage containers
 * @author student
 */
public class LoadBalancer {
    private List<FileStorageContainer> containers;
    private HashMap<String, Boolean> containerStatus;
    private HashMap<String, Integer> containerPriorities;
    private int currentContainerIndex;
    private String currentAlgorithm;
    private Random random;
    
    /**
     * Constructor - initializes the load balancer
     */
    public LoadBalancer() {
        containers = new ArrayList<>();
        containerStatus = new HashMap<>();
        containerPriorities = new HashMap<>();
        currentContainerIndex = 0;
        currentAlgorithm = "RoundRobin";
        random = new Random();
    }
    
    /**
     * Add a new container to the load balancer
     * @param container The storage container to add
     */
    public void addContainer(FileStorageContainer container) {
        containers.add(container);
        containerStatus.put(container.getId(), true);
        containerPriorities.put(container.getId(), 1);
        System.out.println("Added container: " + container.getId());
    }
    
    /**
     * Set which scheduling algorithm to use
     * @param algorithm The name of the algorithm (RoundRobin, ShortestJob, Priority)
     */
    public void setAlgorithm(String algorithm) {
        if (algorithm.equals("RoundRobin") || 
            algorithm.equals("ShortestJob") || 
            algorithm.equals("Priority")) {
            currentAlgorithm = algorithm;
            System.out.println("Changed to algorithm: " + algorithm);
        }
    }
    
    /**
     * Get the next container based on the current scheduling algorithm
     * @return The selected FileStorageContainer
     */
    public FileStorageContainer getNextContainer() {
        // Check if any containers are available
        if (containers.isEmpty()) {
            return null;
        }
        
        FileStorageContainer selected = null;
        
        // Use the current algorithm to select container
        if (currentAlgorithm.equals("RoundRobin")) {
            selected = roundRobinSelect();
        }
        else if (currentAlgorithm.equals("ShortestJob")) {
            selected = shortestJobSelect();
        }
        else if (currentAlgorithm.equals("Priority")) {
            selected = prioritySelect();
        }
        
        return selected;
    }
    
    /**
     * Round Robin selection algorithm
     * @return The next container in the rotation
     */
    private FileStorageContainer roundRobinSelect() {
        // Reset index if we've reached the end
        if (currentContainerIndex >= containers.size()) {
            currentContainerIndex = 0;
        }
        
        // Get next container and increment index
        FileStorageContainer container = containers.get(currentContainerIndex);
        currentContainerIndex++;
        
        return container;
    }
    
    /**
     * Shortest Job Next selection algorithm
     * @return The container with fewest active connections
     */
    private FileStorageContainer shortestJobSelect() {
        FileStorageContainer selected = containers.get(0);
        int minConnections = selected.getActiveConnections();
        
        // Find container with least connections
        for (FileStorageContainer container : containers) {
            if (container.getActiveConnections() < minConnections) {
                selected = container;
                minConnections = container.getActiveConnections();
            }
        }
        
        return selected;
    }
    
    /**
     * Priority-based selection algorithm
     * @return Container based on priority level
     */
    private FileStorageContainer prioritySelect() {
        FileStorageContainer selected = containers.get(0);
        int highestPriority = containerPriorities.get(selected.getId());
        
        // Find container with highest priority
        for (FileStorageContainer container : containers) {
            int priority = containerPriorities.get(container.getId());
            if (priority > highestPriority) {
                selected = container;
                highestPriority = priority;
            }
        }
        
        return selected;
    }
    
    /**
     * Set the priority level for a container
     * @param containerId The container's ID
     * @param priority The priority level to set
     */
    public void setContainerPriority(String containerId, int priority) {
        if (containerPriorities.containsKey(containerId)) {
            containerPriorities.put(containerId, priority);
            System.out.println("Set priority " + priority + " for container " + containerId);
        }
    }
    
    /**
     * Simulate network delay based on traffic level
     * @param trafficLevel LOW, MEDIUM, or HIGH
     */
    public void simulateDelay(String trafficLevel) {
        // Base delay 30-90 seconds
        int baseDelay = 30000 + random.nextInt(60000);
        
        // Adjust for traffic
        int finalDelay = baseDelay;
        if (trafficLevel.equals("HIGH")) {
            finalDelay = (int)(baseDelay * 2.0);
        }
        else if (trafficLevel.equals("LOW")) {
            finalDelay = (int)(baseDelay * 0.5);
        }
        
        try {
            Thread.sleep(finalDelay);
        } catch (InterruptedException e) {
            System.out.println("Delay interrupted");
        }
    }
    
    /**
     * Update the health status of a container
     * @param containerId The container's ID
     * @param isHealthy The container's health status
     */
    public void updateContainerHealth(String containerId, boolean isHealthy) {
        if (containerStatus.containsKey(containerId)) {
            containerStatus.put(containerId, isHealthy);
            System.out.println("Container " + containerId + " health: " + isHealthy);
        }
    }
}
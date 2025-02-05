package com.mycompany.javafxapplication1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

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
    private TrafficLevel currentTrafficLevel;
    private long lastTrafficUpdate;
    private static final long TRAFFIC_UPDATE_INTERVAL = 30000;
    
    private enum TrafficLevel {
        LOW(0.5),    // 50% of base delay
        MEDIUM(1.0), // 100% of base delay
        HIGH(2.0);   // 200% of base delay
        
        private final double multiplier;
        
        TrafficLevel(double multiplier) {
            this.multiplier = multiplier;
        }
        
        public double getMultiplier() {
            return multiplier;
        }
    }
    
    
    
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
        this.currentTrafficLevel = TrafficLevel.MEDIUM; // Start with medium traffic
        this.lastTrafficUpdate = System.currentTimeMillis();
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
     * Get the next container based on system conditions
     * @return The selected FileStorageContainer
     */
    public FileStorageContainer getNextContainer() {
        if (containers.isEmpty()) {
            return null;
        }
        
        // Check load levels to choose algorithm
        int totalConnections = 0;
        for (FileStorageContainer container : containers) {
            totalConnections += container.getActiveConnections();
        }
        
        // Choose algorithm based on load
        if (totalConnections > containers.size() * 5) {  // High load
            currentAlgorithm = "ShortestJob";
        } else {
            currentAlgorithm = "RoundRobin";  // Normal load
        }
        
        // Select container using current algorithm
        FileStorageContainer selected = null;
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
        if (currentContainerIndex >= containers.size()) {
            currentContainerIndex = 0;
        }
        
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
     * Simulate network delay based on traffic level
     * @param trafficLevel LOW, MEDIUM, or HIGH
     */
    public void simulateDelay(String trafficLevel) {
        int baseDelay = 30000 + random.nextInt(60000);
        
        int finalDelay = baseDelay;
        if (trafficLevel.equals("HIGH")) {
            finalDelay = (int)(baseDelay * 1.5);
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
     * Gets the current traffic level multiplier for delay calculations.
     * This method periodically updates the traffic level to simulate changing network conditions.
     * @return A multiplier value that affects the artificial delay:
     *         - 0.5 for low traffic (faster response)
     *         - 1.0 for medium traffic (normal response)
     *         - 2.0 for high traffic (slower response)
     */
    public double getCurrentTrafficLevel() {
        // Check if it's time to update the traffic level
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTrafficUpdate > TRAFFIC_UPDATE_INTERVAL) {
            updateTrafficLevel();
            lastTrafficUpdate = currentTime;
        }
        
        // Log the current traffic condition for monitoring
        System.out.println("Current traffic level: " + currentTrafficLevel +
                " (multiplier: " + currentTrafficLevel.getMultiplier() + ")");
        
        return currentTrafficLevel.getMultiplier();
    }
    
    /**
     * Updates the traffic level based on simulated network conditions.
     * This creates a more realistic simulation of varying network traffic.
     */
    private void updateTrafficLevel() {
        // Randomly select a new traffic level
        int randomValue = random.nextInt(100);
        
        // Distribution: 20% chance of high traffic, 50% medium, 30% low
        if (randomValue < 20) {
            currentTrafficLevel = TrafficLevel.HIGH;
        } else if (randomValue < 70) {
            currentTrafficLevel = TrafficLevel.MEDIUM;
        } else {
            currentTrafficLevel = TrafficLevel.LOW;
        }
        
        System.out.println("Traffic level updated to: " + currentTrafficLevel);
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
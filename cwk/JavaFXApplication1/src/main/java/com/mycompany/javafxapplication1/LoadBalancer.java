package com.mycompany.javafxapplication1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private final ScheduledExecutorService healthCheckExecutor;
    private static final int HEALTH_CHECK_INTERVAL = 300;
    private DB database;
    
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
        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        this.database = new DB();
        startHealthChecks();
    }
    
    //for health checking
    private void startHealthChecks() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            for (FileStorageContainer container : containers) {
                try {
                    boolean isHealthy = container.checkHealth();
                    updateContainerHealth(container.getId(), isHealthy);
                    
                    // Log container status changes
                    Boolean previousStatus = containerStatus.get(container.getId());
                    if (previousStatus != null && previousStatus != isHealthy) {
                        System.out.println("Container " + container.getId() + 
                                         " health status changed from " + 
                                         previousStatus + " to " + isHealthy);
                    }
                } catch (Exception e) {
                    System.err.println("Error during health check for container " + 
                                     container.getId() + ": " + e.getMessage());
                    updateContainerHealth(container.getId(), false);
                }
            }
        }, 0, HEALTH_CHECK_INTERVAL, TimeUnit.SECONDS);
    }
    
    // Make sure to clean up when shutting down
    public void shutdown() {
        healthCheckExecutor.shutdown();
        try {
            if (!healthCheckExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
    
    public FileStorageContainer getContainerForFileChunk(String fileId, int chunkNumber, String operationType) {
        if (containers.isEmpty()) {
            return null;
        }
        
        if ("UPLOAD".equalsIgnoreCase(operationType)) {
            // For uploads, use load balancing to select container
            System.out.println("Upload operation - selecting container using load balancing");
            return getNextContainer();
        } 
        else if ("DOWNLOAD".equalsIgnoreCase(operationType)) {
            try {
                // Get file metadata from database
                FileMetadata metadata = database.getFileMetadata(fileId);
                if (metadata != null) {
                    // Get the container ID where this chunk was stored
                    String containerId = metadata.getContainerForChunk(chunkNumber);
                    if (containerId != null) {
                        System.out.println("Download operation - retrieving from original container: " + containerId);
                        // Find and return the matching container
                        return containers.stream()
                                .filter(c -> c.getId().equals(containerId))
                                .findFirst()
                                .orElse(null);
                    }
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Error retrieving file metadata: " + e.getMessage());
            }
        }
        
        // Fallback for unknown operation type or if container lookup fails
        System.err.println("Warning: Using fallback container selection");
        return getNextContainer();
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
     * Update the health status of a container
     * @param containerId The container's ID
     * @param isHealthy The container's health status
     */
    public void updateContainerHealth(String containerId, boolean isHealthy) {
        if (containerStatus.containsKey(containerId)) {
            containerStatus.put(containerId, isHealthy);
            if(isHealthy){
                System.out.println("Container " + containerId + " health: Ok");
            }
            else{
                System.out.println("Container " + containerId + " health: Poor");
            }
            
        }
    }
}
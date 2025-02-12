package com.mycompany.javafxapplication1;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    
    private static final int MIN_CONTAINERS = 4;
    private static final int MAX_CONTAINERS = 10;
    private static final double SCALE_UP_THRESHOLD = 0.8;   // 80% load triggers scale up
    private static final double SCALE_DOWN_THRESHOLD = 0.3; // 30% load triggers scale down
    
    private Map<String, ContainerMetrics> containerMetrics;
    private final RequestQueue requestQueue;
    
    public class ContainerMetrics {
        private int activeConnections;
        private long bytesProcessed;
        private long lastUsed;
        
        public ContainerMetrics() {
            this.activeConnections = 0;
            this.bytesProcessed = 0;
            this.lastUsed = System.currentTimeMillis();
        }
        
        public void updateMetrics(int connections, long bytes) {
            this.activeConnections = connections;
            this.bytesProcessed += bytes;
            this.lastUsed = System.currentTimeMillis();
        }
    }
    
    /**
     * Constructor - initializes the load balancer
     */
    public LoadBalancer() {
        containers = new ArrayList<>();
        containerStatus = new HashMap<>();
        containerPriorities = new HashMap<>();
        containerMetrics = new HashMap<>();//REMOVE
        currentContainerIndex = 0;
        currentAlgorithm = "RoundRobin";
        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        this.database = new DB();
        this.requestQueue = new RequestQueue();
        startHealthChecks();
        startScalingMonitor();
        startRequestProcessor();
    }
    
    private void startRequestProcessor() {
        Thread processor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Request request = requestQueue.getNextRequest();
                    if (request != null) {
                        processRequest(request);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        processor.setDaemon(true);
        processor.start();
    }
    
    void processRequest(Request request) {
        try {
            FileStorageContainer container = getContainerForFileChunk(
                    request.getFileId(),
                    request.getChunkNumber(),
                    request.getOperationType()
            );
            
            if (container != null) {
                System.out.println("Assigned container " + container.getId() +
                        " to request from user " + request.getUserId());
            }
        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
        }
    }
    
    private void startScalingMonitor() {
        ScheduledExecutorService scalingExecutor = Executors.newSingleThreadScheduledExecutor();
        scalingExecutor.scheduleAtFixedRate(this::checkScaling, 0, 60, TimeUnit.SECONDS);
    }
    
    // Method to check if scaling is needed
    private void checkScaling() {
        double averageLoad = calculateAverageLoad();
        System.out.println("Current system load: " + (averageLoad * 100) + "%");
        
        if (averageLoad > SCALE_UP_THRESHOLD && containers.size() < MAX_CONTAINERS) {
            scaleUp();
        } else if (averageLoad < SCALE_DOWN_THRESHOLD && containers.size() > MIN_CONTAINERS) {
            scaleDown();
        }
    }
    //REMOVE
    public void simulateLoad(int numberOfConnections) {
        if (containers.isEmpty()) {
            System.out.println("No containers available to simulate load");
            return;
        }
        
        int connectionsPerContainer = numberOfConnections / containers.size();
        
        System.out.println("\n=== Starting Load Simulation ===");
        System.out.println("Total simulated connections: " + numberOfConnections);
        System.out.println("Connections per container: " + connectionsPerContainer);
        System.out.println("Current number of containers: " + containers.size());
        
        // Update active connections for each container
        for (FileStorageContainer container : containers) {
            // Initialize metrics if not exists
            containerMetrics.putIfAbsent(container.getId(), new ContainerMetrics());
            
            // Reset existing connections
            while (container.getActiveConnections() > 0) {
                container.decrementActiveConnections();
            }
            
            // Simulate new container load
            for (int i = 0; i < connectionsPerContainer; i++) {
                container.incrementActiveConnections();
            }
            
            // Update metrics
            ContainerMetrics metrics = containerMetrics.get(container.getId());
            metrics.updateMetrics(container.getActiveConnections(), connectionsPerContainer * 1024); // Simulate 1KB per connection
            
            System.out.println("Container " + container.getId() +
                    " active connections: " + container.getActiveConnections());
        }
        
        // Force a scaling check
        checkScaling();
    }
    
   
  
    
    //-----------------------------------------------------------------------------
    
    // Calculate average load across containers
    private double calculateAverageLoad() {
        if (containers.isEmpty()) return 0.0;
        
        int totalConnections = 0;
        for (FileStorageContainer container : containers) {
            totalConnections += container.getActiveConnections();
        }
        
        return (double) totalConnections / (containers.size() * 10); // Assuming 10 connections per container is 100% load
    }
    
    // Method to add a new container
    public void scaleUp() {
        int newContainerNumber = containers.size() + 1;
        String containerId = "container-" + newContainerNumber;
        
        try {
            // Create new container using Docker API
            Process process = Runtime.getRuntime().exec(
                    "docker run -d --name " + containerId + " " +
                            "--network comp20081-network " + // Connect to existing network
                            "-v " + containerId + ":/storage " + // Mount storage volume
                                    "ubuntu:latest"
            );
            
            // Wait for container to start
            if (process.waitFor() == 0) {
                // Add new container to load balancer
                FileStorageContainer newContainer = new FileStorageContainer(
                        containerId,
                        "/storage/" + containerId
                );
                addContainer(newContainer);
                
                System.out.println("Successfully scaled up: Added " + containerId);
            } else {
                System.err.println("Failed to create new container: " + containerId);
            }
            
        } catch (Exception e) {
            System.err.println("Error scaling up: " + e.getMessage());
        }
    }
    
    // Method to remove a container
    public void scaleDown() {
        if (containers.size() <= MIN_CONTAINERS) {
            return;
        }
        
        // Find least used container
        FileStorageContainer containerToRemove = findLeastUsedContainer();
        if (containerToRemove == null) {
            return;
        }
        
        try {
            // Migrate data from container before removing
            moveContainerData(containerToRemove);
            
            // Remove container using Docker API
            Process process = Runtime.getRuntime().exec(
                    "docker rm -f " + containerToRemove.getId()
            );
            
            if (process.waitFor() == 0) {
                containers.remove(containerToRemove);
                containerStatus.remove(containerToRemove.getId());
                containerMetrics.remove(containerToRemove.getId());
                
                System.out.println("Successfully scaled down: Removed " + containerToRemove.getId());
            }
            
        } catch (Exception e) {
            System.err.println("Error scaling down: " + e.getMessage());
        }
    }
    
    // Helper method to find least used container
    private FileStorageContainer findLeastUsedContainer() {
        if (containers.isEmpty()) return null;
        
        return containers.stream()
                .min((c1, c2) -> {
                    ContainerMetrics m1 = containerMetrics.get(c1.getId());
                    ContainerMetrics m2 = containerMetrics.get(c2.getId());
                    return Long.compare(m1.bytesProcessed, m2.bytesProcessed);
                })
                .orElse(null);
    }
    
    // Helper method to migrate data before removing container
    private void moveContainerData(FileStorageContainer container) throws Exception {
        // Get list of files in container
        List<FileMetadata> containerFiles = database.getAllFiles().stream()
                .filter(file -> file.getContainerForChunk(0).equals(container.getId()))
                .collect(Collectors.toList());
        
        // Redistribute files to other containers
        for (FileMetadata file : containerFiles) {
            FileStorageContainer newContainer = getNextContainer();
            if (newContainer != null && !newContainer.equals(container)) {
                // Migrate file chunks to new container
                for (int i = 0; i < file.getTotalChunks(); i++) {
                    if (file.getContainerForChunk(i).equals(container.getId())) {
                        // Read chunk from old container
                        byte[] chunkData = container.retrieveFileChunk(
                                file.getFileId(), i
                        );
                        
                        // Store in new container
                        newContainer.storeFileChunk(
                                file.getFileId(), i, chunkData
                        );
                        
                        // Update metadata
                        file.addChunkLocation(i, newContainer.getId());
                    }
                }
            }
        }
        
        // Update database with new locations
        for (FileMetadata file : containerFiles) {
            database.saveFileMetadata(file);
        }
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
        requestQueue.shutdown();
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
    
       public RequestQueue getRequestQueue() {
    return requestQueue;
}
    
}
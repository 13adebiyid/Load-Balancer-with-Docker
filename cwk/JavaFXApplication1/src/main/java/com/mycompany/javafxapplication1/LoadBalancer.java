package com.mycompany.javafxapplication1;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * LoadBalancer class distributes work across file storage containers
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
    private static final double SCALE_UP_THRESHOLD = 0.8;   // 80% 
    private static final double SCALE_DOWN_THRESHOLD = 0.3; // 30% 
    private static final int CHECK_SCALING_INTERVAL = 300;
    private final DockerContainerManager dockerManager;
    
    private Map<String, ContainerMetrics> containerMetrics;
    
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
    //test-----------------------------------
    public int getContainerCount() {
        return containers.size();
    }
    
    public List<FileStorageContainer> getContainers() {
        return new ArrayList<>(containers);  
    }
    //test------------------------------------
    
    /**
     * Constructor initializes load balancer
     */
    public LoadBalancer() {
        containers = new ArrayList<>();
        containerStatus = new HashMap<>();
        containerPriorities = new HashMap<>();
        
        currentContainerIndex = 0;
        currentAlgorithm = "RoundRobin";
        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        this.database = new DB();
        this.dockerManager = new DockerContainerManager();
        startHealthChecks();
        startScalingMonitor();
    }
    
    private void startScalingMonitor() {
        ScheduledExecutorService scalingExecutor = Executors.newSingleThreadScheduledExecutor();
        scalingExecutor.scheduleAtFixedRate(
            this::checkScaling, 
            0, 
            CHECK_SCALING_INTERVAL, 
            TimeUnit.SECONDS
        );
    }
    
//     check if scaling is needed
    private void checkScaling() {
        double averageLoad = calculateAverageLoad();
        System.out.println("\n=== Checking System Load ===");
        System.out.println("Current system load: " + String.format("%.2f", averageLoad * 100) + "%");
        
        int currentContainers = dockerManager.getCurrentContainerCount();
        if (currentContainers == -1) {
            System.err.println("Failed to get current container count");
            return;
        }
        
        System.out.println("Current container count: " + currentContainers);
        
        boolean needsScaling = false;
        int targetContainers = currentContainers;

        if (averageLoad > SCALE_UP_THRESHOLD && currentContainers < DockerContainerManager.MAX_CONTAINERS) {
            targetContainers = Math.min(currentContainers + 2, DockerContainerManager.MAX_CONTAINERS);
            System.out.println("High load detected - scaling up to " + targetContainers + " containers");
            needsScaling = true;
        } else if (averageLoad < SCALE_DOWN_THRESHOLD && currentContainers > DockerContainerManager.MIN_CONTAINERS) {
            targetContainers = Math.max(currentContainers - 1, DockerContainerManager.MIN_CONTAINERS);
            System.out.println("Low load detected - scaling down to " + targetContainers + " containers");
            needsScaling = true;
        }

        if (needsScaling) {
            dockerManager.scaleContainers(targetContainers);
            try {
                Thread.sleep(5000); 
                updateContainerList();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("=== Scaling Check Complete ===\n");
    }
    
    private void updateContainerList() {
        int currentCount = dockerManager.getCurrentContainerCount();
        if (currentCount > 0) {
            List<FileStorageContainer> newContainers = new ArrayList<>();
            for (int i = 1; i <= currentCount; i++) {
                String containerId = "container-" + i;
                FileStorageContainer container = new FileStorageContainer(containerId,"/storage/" + containerId);
                newContainers.add(container);
                containerStatus.put(containerId, true);
                containerPriorities.put(containerId, 1);
                
                if (!containerMetrics.containsKey(containerId)) {
                    containerMetrics.put(containerId, new ContainerMetrics());
                }
            }
            containers = newContainers;
            System.out.println("Updated container list - now managing " + containers.size() + " containers");
        }
    }

    //shutdown health checks and container management to free up space when not needed
    public void shutdown() {
        if (healthCheckExecutor != null) {
            healthCheckExecutor.shutdown();
            System.out.println("Warning! Health checks have stopped.");
        }
        if (dockerManager != null) {
            dockerManager.shutdown();
            System.out.println("Warning! Dynamic system scaling is no longer operational.");
        }
    }
    
    // Calculate average load across containers
    private double calculateAverageLoad() {
        if (containers.isEmpty()) return 0.0;
        
        int totalConnections = 0;
        for (FileStorageContainer container : containers) {
            totalConnections += container.getActiveConnections();
        }
        
        return (double) totalConnections / (containers.size() * 10); // Assuming 10 connections per container = 100% load
    }
    
    //add a new container
    public void scaleUp() {
        int newContainerNumber = containers.size() + 1;
        String containerId = "container-" + newContainerNumber;
        
        try {
            Process process = Runtime.getRuntime().exec("docker run -d --name " + containerId + " " +"--network comp20081-network " +"-v " + containerId + ":/storage " +  "ubuntu:latest");
            
            if (process.waitFor() == 0) {
                FileStorageContainer newContainer = new FileStorageContainer(containerId,"/storage/" + containerId);
                addContainer(newContainer);
                
                System.out.println("Successfully scaled up: Added " + containerId);
            } else {
                System.err.println("Failed to create new container: " + containerId);
            }
            
        } catch (Exception e) {
            System.err.println("Error scaling up: " + e.getMessage());
        }
    }
    
    //remove a container
    public void scaleDown() {
        if (containers.size() <= MIN_CONTAINERS) {
            return;
        }
        
        FileStorageContainer containerToRemove = findLeastUsedContainer();
        if (containerToRemove == null) {
            return;
        }
        
        try {
            moveContainerData(containerToRemove);
            
            Process process = Runtime.getRuntime().exec("docker rm -f " + containerToRemove.getId());
            
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
    
    //find least used container
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
    
    private void moveContainerData(FileStorageContainer container) throws Exception {
        List<FileMetadata> containerFiles = database.getAllFiles().stream()
                .filter(file -> file.getContainerForChunk(0).equals(container.getId()))
                .collect(Collectors.toList());
        
        for (FileMetadata file : containerFiles) {
            FileStorageContainer newContainer = getNextContainer();
            if (newContainer != null && !newContainer.equals(container)) {
                for (int i = 0; i < file.getTotalChunks(); i++) {
                    if (file.getContainerForChunk(i).equals(container.getId())) {
                        byte[] chunkData = container.retrieveFileChunk(file.getFileId(), i);
                        
                        newContainer.storeFileChunk(file.getFileId(), i, chunkData);
                        
                        file.addChunkLocation(i, newContainer.getId());
                    }
                }
            }
        }
        
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
                    
                    Boolean previousStatus = containerStatus.get(container.getId());
                    if (previousStatus != null && previousStatus != isHealthy) {
                        System.out.println(container.getId() + " health status changed from " + previousStatus + " to " + isHealthy);
                    }
                } catch (Exception e) {
                    System.err.println("Error during health check for container " +container.getId() + ": " + e.getMessage());
                    updateContainerHealth(container.getId(), false);
                }
            }
        }, 0, HEALTH_CHECK_INTERVAL, TimeUnit.SECONDS);
    }
    
    /**
     * Add new container to load balancer
     */
    public void addContainer(FileStorageContainer container) {
        containers.add(container);
        containerStatus.put(container.getId(), true);
        containerPriorities.put(container.getId(), 1);
        System.out.println("Added: " + container.getId());
    }
    
    public FileStorageContainer getContainerForFileChunk(String fileId, int chunkNumber, String operationType) {
        
        
        if (containers.isEmpty()) {
            return null;
        }
        
        if ("UPLOAD".equalsIgnoreCase(operationType)) {
            System.out.println("Upload operation - selecting container using load balancing");
            return getNextContainer();
        }
        else if ("DOWNLOAD".equalsIgnoreCase(operationType)) {
            try {
                FileMetadata metadata = database.getFileMetadata(fileId);
                if (metadata != null) {
                    String containerId = metadata.getContainerForChunk(chunkNumber);
                    if (containerId != null) {
                        System.out.println("Download operation - retrieving from original " + containerId);
                        return containers.stream().filter(c -> c.getId().equals(containerId)).findFirst().orElse(null);
                    }
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Error retrieving file metadata: " + e.getMessage());
            }
        }
        
        System.err.println("Warning: Using fallback container selection");
        return getNextContainer();
    }
   
    
    /**
     * Get next container based on system conditions
     */
    public FileStorageContainer getNextContainer() {
        if (containers.isEmpty()) {
            return null;
        }
        
        int totalConnections = 0;
        for (FileStorageContainer container : containers) {
            totalConnections += container.getActiveConnections();
        }
        
        if (totalConnections > containers.size() * 5) {  // High load
            currentAlgorithm = "ShortestJob";
        } else {
            currentAlgorithm = "RoundRobin";  // Normal load
        }
        
        FileStorageContainer selected = null;
        if (currentAlgorithm.equals("RoundRobin")) {
            selected = roundRobinSelect();
            System.out.println("Round Robin is being used for ");
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
     * Priority based selection algorithm
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
     */
    public void updateContainerHealth(String containerId, boolean isHealthy) {
        if (containerStatus.containsKey(containerId)) {
            containerStatus.put(containerId, isHealthy);
            if(isHealthy){
                System.out.println(containerId + " health: Ok");
            }
            else{
                System.out.println(containerId + " health: Poor");
            }
            
        }
    }

}
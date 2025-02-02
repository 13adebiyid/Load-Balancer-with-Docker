package com.mycompany.javafxapplication1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

/**
 * Represents a single storage container in the distributed file system
 * Each container can store file chunks and manage concurrent access
 * @author student
 */
public class FileStorageContainer {
    private String containerId;
    private String storagePath;
    private AtomicInteger activeConnections;
    private Random random;
    
    /**
     * Constructor - sets up the storage container
     * @param containerId Unique identifier for this container
     * @param storagePath Directory path where files will be stored
     */
    public FileStorageContainer(String containerId, String storagePath) {
        this.containerId = containerId;
        this.storagePath = storagePath;
        this.activeConnections = new AtomicInteger(0);
        this.random = new Random();
        
        // Create the storage directory if it doesn't exist
        File storage = new File(storagePath);
        if (!storage.exists()) {
            storage.mkdirs();
        }
        
        System.out.println("Created storage container: " + containerId);
    }
    
    /**
     * Store a chunk of a file in this container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The sequence number of this chunk
     * @param data The actual chunk data to store
     * @param trafficLevel Affects the artificial delay (1.0 = normal)
     */
    public void storeFileChunk(String fileId, int chunkNumber, byte[] data, double trafficLevel) 
            throws IOException, InterruptedException {
        // Increment active connections counter
        activeConnections.incrementAndGet();
        
        try {
            // Create the file chunk path
            String chunkPath = storagePath + File.separator + 
                             fileId + "_chunk_" + chunkNumber;
            
            // Simulate network delay
            simulateNetworkDelay(trafficLevel);
            
            // Write the chunk to disk
            try (FileOutputStream fos = new FileOutputStream(chunkPath)) {
                fos.write(data);
                fos.flush();
            }
            
            System.out.println("Stored chunk " + chunkNumber + " of file " + fileId + 
                             " in container " + containerId);
                             
        } finally {
            // Always decrement active connections counter
            activeConnections.decrementAndGet();
        }
    }
    
    /**
     * Retrieve a chunk of a file from this container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The sequence number of the chunk to retrieve
     * @param trafficLevel Affects the artificial delay (1.0 = normal)
     * @return The chunk data
     */
    public byte[] retrieveFileChunk(String fileId, int chunkNumber, double trafficLevel) 
            throws IOException, InterruptedException {
        // Increment active connections counter
        activeConnections.incrementAndGet();
        
        try {
            // Get the chunk file path
            String chunkPath = storagePath + File.separator + 
                             fileId + "_chunk_" + chunkNumber;
            
            // Simulate network delay
            simulateNetworkDelay(trafficLevel);
            
            // Read the chunk from disk
            File chunkFile = new File(chunkPath);
            byte[] data = new byte[(int) chunkFile.length()];
            
            try (FileInputStream fis = new FileInputStream(chunkFile)) {
                fis.read(data);
            }
            
            System.out.println("Retrieved chunk " + chunkNumber + " of file " + fileId + 
                             " from container " + containerId);
            
            return data;
            
        } finally {
            // Always decrement active connections counter
            activeConnections.decrementAndGet();
        }
    }
    
    /**
     * Simulate network delay based on traffic level
     * @param trafficLevel Multiplier for the base delay (1.0 = normal)
     */
    private void simulateNetworkDelay(double trafficLevel) throws InterruptedException {
        // Base delay between 30-90 seconds as per requirements
        int baseDelay = 30000 + random.nextInt(60000);
        
        // Adjust delay based on traffic level
        int actualDelay = (int)(baseDelay * trafficLevel);
        Thread.sleep(actualDelay);
    }
    
    /**
     * Get the number of active connections to this container
     * @return Current number of active connections
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }
    
    /**
     * Get the container's ID
     * @return Container ID
     */
    public String getId() {
        return containerId;
    }
}
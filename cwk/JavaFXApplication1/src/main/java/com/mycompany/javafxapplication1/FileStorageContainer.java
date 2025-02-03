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
    // In FileStorageContainer.java, modify storeFileChunk:
    
    public void storeFileChunk(String fileId, int chunkNumber, byte[] data, double trafficLevel)
            throws IOException, InterruptedException {
        
        // First, construct the path where the chunk will be stored
        // We use File.separator to ensure correct path separators on different operating systems
        String chunkPath = storagePath + File.separator + fileId + "_chunk_" + chunkNumber;
        
        // Create encryption instance
        FileEncryption encryption = new FileEncryption();
        
        // Encrypt the chunk data
        byte[] encryptedData = encryption.encryptData(data);
        if (encryptedData == null) {
            throw new IOException("Failed to encrypt chunk data");
        }
        
        // Store the encryption key in the database
        String keyString = encryption.getKeyAsString();
        DB db = new DB();
        try {
            db.storeEncryptionKey(fileId, chunkNumber, keyString);
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to store encryption key");
        }
        
        // Write the encrypted data to the file
        try (FileOutputStream fos = new FileOutputStream(chunkPath)) {
            fos.write(encryptedData);
            fos.flush();
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
        
        // Read the encrypted data
        String chunkPath = String.format("%s/%s_chunk_%d", storagePath, fileId, chunkNumber);
        byte[] encryptedData;
        try (FileInputStream fis = new FileInputStream(chunkPath)) {
            encryptedData = fis.readAllBytes();
        }
        
        // Get the encryption key from database
        DB db = new DB();
        String keyString;
        try {
            keyString = db.getEncryptionKey(fileId, chunkNumber);
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to retrieve encryption key");
        }
        
        // Create encryption instance and decrypt
        FileEncryption encryption = FileEncryption.fromKey(keyString);
        if (encryption == null) {
            throw new IOException("Failed to create encryption instance");
        }
        
        byte[] decryptedData = encryption.decryptData(encryptedData);
        if (decryptedData == null) {
            throw new IOException("Failed to decrypt chunk data");
        }
        
        return decryptedData;
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
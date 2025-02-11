package com.mycompany.javafxapplication1;

import com.jcraft.jsch.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Represents a single storage container in the distributed file system
 * Each container can store file chunks and manage concurrent access
 * @author student
 */
public class FileStorageContainer {
    private String containerId;
    private String storagePath;
    private AtomicInteger activeConnections;
    private String containerHost;  // Add this for network communication
    private int containerPort;
    
    /**
     * Constructor - sets up the storage container
     * @param containerId Unique identifier for this container
     * @param storagePath Directory path where files will be stored
     */
    public FileStorageContainer(String containerId, String storagePath) {
        this.containerId = containerId;
        this.storagePath = storagePath;
        
        // Map container IDs to their Docker service names
        switch(containerId) {
            case "container-1":
                this.containerHost = "storage1";
                break;
            case "container-2":
                this.containerHost = "storage2";
                break;
            case "container-3":
                this.containerHost = "storage3";
                break;
            case "container-4":
                this.containerHost = "storage4";
                break;
        }
        this.containerPort = 22;  // SSH port as defined in docker-compose.yml
        
        System.out.println("Initializing container " + containerId +
                " with host: " + containerHost +
                " and storage path: " + storagePath);
    }
    
    /**
     * Store a chunk of a file in this container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The sequence number of this chunk
     * @param data The actual chunk data to store
     * @param trafficLevel Affects the artificial delay (1.0 = normal)
     */
    // In FileStorageContainer.java, modify storeFileChunk:
    
    public void storeFileChunk(String fileId, int chunkNumber, byte[] data) throws IOException {
        ChannelSftp sftpChannel = null;
        try {
            // Basic SSH connection with correct credentials
            JSch jsch = new JSch();
            Session session = jsch.getSession("ntu-user",
                    this.containerId.replace("container-", "storage"), 22);
            session.setPassword("ntu-user");
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            
            // Store the file chunk
            String chunkPath = storagePath + "/" + fileId + "_chunk_" + chunkNumber;
            try (ByteArrayInputStream dataStream = new ByteArrayInputStream(data)) {
                sftpChannel.put(dataStream, chunkPath);
            }
            
        } catch (Exception e) {
            throw new IOException("Failed to store chunk: " + e.getMessage());
        } finally {
            if (sftpChannel != null) {
                sftpChannel.disconnect();
            }
        }
    }
    
    /**
     * Retrieve a chunk of a file from this container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The sequence number of the chunk to retrieve
     * @return The chunk data
     */
    public byte[] retrieveFileChunk(String fileId, int chunkNumber)
            throws IOException, InterruptedException {
        try {
            // Create SSH session
            JSch jsch = new JSch();
            Session session = jsch.getSession("ntu-user", containerHost, containerPort);
            session.setPassword("ntu-user");
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            session.connect();
            
            // Create SFTP channel
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            
            // Retrieve the data
            String chunkPath = storagePath + "/" + fileId + "_chunk_" + chunkNumber;
            byte[] data;
            
            try (ByteArrayOutputStream dataStream = new ByteArrayOutputStream()) {
                sftpChannel.get(chunkPath, dataStream);
                data = dataStream.toByteArray();
            }
            
            sftpChannel.disconnect();
            session.disconnect();
            
            System.out.println("Successfully retrieved chunk " + chunkNumber +
                    " from container " + containerId);
            
            return data;
            
        } catch (JSchException e) {
            throw new IOException("Failed to connect to storage container: " + e.getMessage());
        } catch (SftpException e) {
            throw new IOException("Failed to retrieve file chunk: " + e.getMessage());
        }
    }
    
    public boolean checkHealth() {
        Session session = null;
        ChannelSftp sftpChannel = null;
        
        try {
            // Test SSH connectivity
            JSch jsch = new JSch();
            session = jsch.getSession("ntu-user", containerHost, containerPort);
            session.setPassword("ntu-user");
            
            // Configure SSH connection
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            session.connect(5000);  // 5 second timeout
            
            // Test SFTP functionality
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            
            // Test write capability
            String healthCheckFileName = ".health_check_" + System.currentTimeMillis();
            String healthCheckPath = storagePath + "/" + healthCheckFileName;
            
            // write small test file
            byte[] testData = "health check".getBytes();
            try (ByteArrayInputStream dataStream = new ByteArrayInputStream(testData)) {
                sftpChannel.put(dataStream, healthCheckPath);
            }
            
            // Test read capability
            try (ByteArrayOutputStream readStream = new ByteArrayOutputStream()) {
                sftpChannel.get(healthCheckPath, readStream);
                
                // Verify the data 
                boolean dataMatches = Arrays.equals(testData, readStream.toByteArray());
                if (!dataMatches) {
                    System.err.println("Health check failed: Data corruption detected in container " + containerId);
                    return false;
                }
            }
            
            try {
                sftpChannel.rm(healthCheckPath);
            } catch (SftpException e) {
                System.err.println("Warning: Could not remove health check file: " + e.getMessage());
            }
            
            System.out.println("Health check passed for container " + containerId);
            return true;
            
        } catch (JSchException e) {
            System.err.println("Health check failed for container " + containerId + ": SSH connection error: " + e.getMessage());
            return false;
        } catch (SftpException e) {
            System.err.println("Health check failed for container " + containerId + ": SFTP operation error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Health check failed for container " + containerId + ": Unexpected error: " + e.getMessage());
            return false;
        } finally {
            // clean connections
            if (sftpChannel != null) {
                sftpChannel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
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
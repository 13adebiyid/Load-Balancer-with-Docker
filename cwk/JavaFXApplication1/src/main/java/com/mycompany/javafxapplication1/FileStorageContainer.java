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
        this.activeConnections = new AtomicInteger(0); // Initialize the AtomicInteger
        
        // Map container IDs to their Docker service names
        this.containerHost = containerId.replace("container-", "storage");
        this.containerPort = 22;
        
        System.out.println("Initializing container " + containerId + " with host: " + containerHost + " and storage path: " + storagePath);
    }
    
    /**
     * Store a chunk of a file in this container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The sequence number of this chunk
     * @param data The actual chunk data to store
     */
    // In FileStorageContainer.java, modify storeFileChunk:
    
    public void storeFileChunk(String fileId, int chunkNumber, byte[] data) throws IOException {
        ChannelSftp sftpChannel = null;
        Session session = null;
        
        try {
            JSch jsch = new JSch();
            // Using root credentials as the container is set up with root ownership
            session = jsch.getSession("root", containerHost, containerPort);
            session.setPassword("root");
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            System.out.println("Connecting to " + containerHost + " as root");
            session.connect(5000);
            
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            
            // Ensure storage directory exists
            try {
                sftpChannel.mkdir(storagePath);
                System.out.println("Created storage directory: " + storagePath);
            } catch (SftpException e) {
                // Directory might already exist
                System.out.println("Storage directory exists: " + storagePath);
            }
            
            // Store the chunk
            String chunkPath = storagePath + "/" + fileId + "_chunk_" + chunkNumber;
            try (ByteArrayInputStream dataStream = new ByteArrayInputStream(data)) {
                sftpChannel.put(dataStream, chunkPath);
                System.out.println("Successfully stored chunk " + chunkNumber + " in " + chunkPath);
            }
            
        } catch (Exception e) {
            String error = "Failed to store chunk in " + containerHost + ": " + e.getMessage();
            System.err.println(error);
            e.printStackTrace();
            throw new IOException(error, e);
        } finally {
            if (sftpChannel != null) {
                sftpChannel.disconnect();
            }
            if (session != null) {
                session.disconnect();
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
        System.out.println("DEBUG: `retrieveFileChunk()` called for fileId: " + fileId + ", chunk: " + chunkNumber);
        Session session = null;
        ChannelSftp sftpChannel = null;
        
        try {
            JSch jsch = new JSch();
            System.out.println("DEBUG: Connecting to " + containerHost + " on port " + containerPort);
            session = jsch.getSession("root", containerHost, containerPort);
            session.setPassword("root");
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            session.connect();
            System.out.println("DEBUG: Connected to " + containerHost);
            
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            System.out.println("DEBUG: SFTP Channel Opened");
            
            String chunkPath = storagePath + "/" + fileId + "_chunk_" + chunkNumber;
            System.out.println("DEBUG: Trying to retrieve file: " + chunkPath);
            byte[] data;
            
            try (ByteArrayOutputStream dataStream = new ByteArrayOutputStream()) {
                sftpChannel.get(chunkPath, dataStream);
                data = dataStream.toByteArray();
                System.out.println("DEBUG: File retrieved successfully. Size: " + data.length + " bytes");
            }
            
            System.out.println("Successfully retrieved chunk " + chunkNumber +
                    " from container " + containerId);
            
            return data;
            
        } catch (JSchException | SftpException e) {
            String error = "Failed to retrieve chunk: " + e.getMessage();
            System.err.println(error);
            e.printStackTrace();
            throw new IOException(error);
        } finally {
            if (sftpChannel != null) sftpChannel.disconnect();
            if (session != null) session.disconnect();
        }
    }
    
    
    
    public boolean checkHealth() {
        Session session = null;
        ChannelSftp sftpChannel = null;
        
        try {
            JSch jsch = new JSch();
            session = jsch.getSession("root", containerHost, containerPort);
            session.setPassword("root");
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            session.connect(5000);
            
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            
            String testPath = storagePath + "/.health_check";
            byte[] testData = "health check".getBytes();
            
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(testData)) {
                sftpChannel.put(inputStream, testPath);
            }
            
            try {
                sftpChannel.rm(testPath);
            } catch (SftpException e) {
                System.err.println("Warning: Could not remove health check file: " + e.getMessage());
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Health check failed for " + containerId + ": " + e.getMessage());
            return false;
        } finally {
            if (sftpChannel != null) sftpChannel.disconnect();
            if (session != null) session.disconnect();
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
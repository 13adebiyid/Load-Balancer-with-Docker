package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with the load balancer service.
 * Implements file operations with artificial delays and traffic simulation
 * as per coursework requirements.
 */
public class LoadBalancerClient {
    private String host;
    private int port;
    private boolean isConnected;
    private Random random;
    
    // Constants for networking and delays
    private static final int CONNECTION_TIMEOUT = 120000; // 2 minutes to account for artificial delays
    private static final int MIN_DELAY = 30000;  // 30 seconds minimum delay
    private static final int MAX_DELAY = 90000;  // 90 seconds maximum delay
    
    // Traffic level multipliers
    private static final double LOW_TRAFFIC_MULTIPLIER = 0.5;
    private static final double MEDIUM_TRAFFIC_MULTIPLIER = 1.0;
    private static final double HIGH_TRAFFIC_MULTIPLIER = 2.0;
    
    /**
     * Constructor - initializes the client with server details
     * @param host The server hostname
     * @param port The server port number
     */
    public LoadBalancerClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.isConnected = false;
        this.random = new Random();
        testConnection();  // Initial connection test
    }
    
    /**
     * Simulates network traffic conditions as required by coursework
     * @return Traffic multiplier to apply to delays
     */
    private double getTrafficMultiplier() {
        // Randomly choose traffic condition with weighted probabilities
        int rand = random.nextInt(100);
        if (rand < 20) {  // 20% chance of high traffic
            System.out.println("Simulating high traffic conditions");
            return HIGH_TRAFFIC_MULTIPLIER;
        } else if (rand < 70) {  // 50% chance of medium traffic
            System.out.println("Simulating medium traffic conditions");
            return MEDIUM_TRAFFIC_MULTIPLIER;
        } else {  // 30% chance of low traffic
            System.out.println("Simulating low traffic conditions");
            return LOW_TRAFFIC_MULTIPLIER;
        }
    }
    
    /**
     * Implements the required artificial delay with traffic simulation
     * @throws InterruptedException if the delay is interrupted
     */
    private void simulateNetworkDelay() throws InterruptedException {
        // Calculate base delay between 30-90 seconds
        int baseDelay = MIN_DELAY + random.nextInt(MAX_DELAY - MIN_DELAY);
        
        // Apply traffic multiplier
        double multiplier = getTrafficMultiplier();
        int actualDelay = (int)(baseDelay * multiplier);
        
        System.out.println("Simulating network delay: " + (actualDelay / 1000) + " seconds");
        Thread.sleep(actualDelay);
    }
    
    /**
     * Uploads a file chunk to a storage container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The chunk sequence number
     * @param data The file chunk data
     * @return The ID of the container that stored the chunk
     * @throws IOException if communication fails
     * @throws ClassNotFoundException if response cannot be deserialized
     */
    public String uploadFileChunk(String fileId, int chunkNumber, byte[] data) 
            throws IOException, ClassNotFoundException {
        
        // Verify connection before attempting upload
        if (!testConnection()) {
            throw new IOException("Cannot connect to load balancer server at " + 
                                host + ":" + port);
        }
        
        try (Socket socket = new Socket()) {
            // Configure socket with appropriate timeout
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
            socket.setSoTimeout(CONNECTION_TIMEOUT);
            
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                
                // Send operation request
                FileOperation operation = new FileOperation("UPLOAD", fileId, chunkNumber, data.length);
                out.writeObject(operation);
                out.flush();
                
                // Get assigned container ID
                String containerId = (String) in.readObject();
                if (containerId == null) {
                    throw new IOException("Server did not assign a container");
                }
                
                // Send the file data
                out.write(data);
                out.flush();
                
                // Wait for upload confirmation
                boolean success = in.readBoolean();
                if (!success) {
                    throw new IOException("Server reported upload failure");
                }
                
                // Simulate network delay after successful upload
                simulateNetworkDelay();
                
                System.out.println("Successfully uploaded chunk " + chunkNumber + 
                                 " to container " + containerId);
                return containerId;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Upload interrupted during delay simulation", e);
        }
    }
    
    /**
     * Downloads a file chunk from a storage container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The chunk sequence number
     * @return The file chunk data
     * @throws IOException if communication fails
     * @throws ClassNotFoundException if response cannot be deserialized
     */
    public byte[] downloadFileChunk(String fileId, int chunkNumber) 
            throws IOException, ClassNotFoundException {
        
        if (!testConnection()) {
            throw new IOException("Cannot connect to load balancer server at " + 
                                host + ":" + port);
        }
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
            socket.setSoTimeout(CONNECTION_TIMEOUT);
            
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                
                // Send download request
                FileOperation operation = new FileOperation("DOWNLOAD", fileId, chunkNumber, 0);
                out.writeObject(operation);
                out.flush();
                
                // Get assigned container ID
                String containerId = (String) in.readObject();
                if (containerId == null) {
                    throw new IOException("Server did not assign a container");
                }
                
                // Read the chunk data
                int dataLength = in.readInt();
                if (dataLength <= 0) {
                    throw new IOException("Invalid data length received: " + dataLength);
                }
                
                byte[] data = new byte[dataLength];
                in.readFully(data);
                
                // Simulate network delay after successful download
                simulateNetworkDelay();
                
                System.out.println("Successfully downloaded chunk " + chunkNumber + 
                                 " from container " + containerId);
                return data;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted during delay simulation", e);
        }
    }
    
    /**
     * Tests if the server is available
     * @return true if the server can be reached
     */
    public boolean testConnection() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
            isConnected = true;
            return true;
        } catch (IOException e) {
            isConnected = false;
            System.err.println("Cannot connect to server: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * @return true if the last operation succeeded in connecting
     */
    public boolean isConnected() {
        return isConnected;
    }
}
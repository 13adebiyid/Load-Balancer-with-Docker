package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.*;
import java.util.Random;

/**
 * Client class for communicating with the load balancer
 * Used by the GUI to send file operations to the load balancer
 */
public class LoadBalancerClient {
    private String host;
    private int port;
    private boolean isConnected;
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds timeout
    private Random random = new Random();
    
    /**
     * Constructor - initializes the client with server details
     * @param host The server hostname
     * @param port The server port number
     */
    public LoadBalancerClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.isConnected = false;
        // Test the connection when client is created
        testConnection();
    }
    
    // Simulate network traffic conditions
    private double getTrafficMultiplier() {
        // Randomly choose traffic condition (LOW: 0.5, MEDIUM: 1.0, HIGH: 2.0)
        double[] multipliers = {0.5, 1.0, 2.0};
        int trafficLevel = random.nextInt(3);
        return multipliers[trafficLevel];
    }
    
    // Implement artificial delay as per requirements
    private void simulateNetworkDelay() throws InterruptedException {
        // Base delay between 30-90 seconds as specified
        int baseDelay = 30000 + random.nextInt(60000);
        
        // Adjust delay based on traffic conditions
        double trafficMultiplier = getTrafficMultiplier();
        int actualDelay = (int)(baseDelay * trafficMultiplier);
        
        System.out.println("Simulating network delay: " + actualDelay/1000 + " seconds");
        Thread.sleep(actualDelay);
    }
    
    /**
     * Uploads a file chunk to a storage container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The chunk sequence number
     * @param data The file chunk data
     * @return The ID of the container that stored the chunk
     */
    public String uploadFileChunk(String fileId, int chunkNumber, byte[] data) 
        throws IOException, ClassNotFoundException {
    
    if (!testConnection()) {
        throw new IOException("Cannot connect to load balancer server at " + host + ":" + port + 
                            ". Please check server is running");
    }
    
    try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
        
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            // Create and send operation request
            FileOperation operation = new FileOperation(
                "UPLOAD", fileId, chunkNumber, data.length
            );
            out.writeObject(operation);
            out.flush();
            
            // Get assigned container ID
            String containerId = (String) in.readObject();
            System.out.println("Server assigned container: " + containerId);
            
            // Send the actual file data
            out.write(data);
            out.flush();
            
            // Wait for confirmation
            boolean success = in.readBoolean();
            if (!success) {
                throw new IOException("Upload failed - server reported failure");
            }
            
            // Simulate network delay according to coursework requirements
            int baseDelay = 30000 + new Random().nextInt(60000); // 30-90 seconds
            System.out.println("Simulating network delay: " + (baseDelay/1000) + " seconds");
            Thread.sleep(baseDelay);
            
            System.out.println("Successfully uploaded chunk " + chunkNumber + 
                             " to container " + containerId);
            return containerId;
        }
            
    } catch (IOException e) {
        isConnected = false;
        throw new IOException("Error during file chunk upload: " + e.getMessage(), e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Network delay simulation interrupted", e);
    }
}
    
    /**
     * Downloads a file chunk from a storage container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The chunk sequence number
     * @return The file chunk data
     */
    public byte[] downloadFileChunk(String fileId, int chunkNumber)
            throws IOException, ClassNotFoundException {
        
        if (!testConnection()) {
            throw new IOException("Cannot connect to load balancer server at " +
                    host + ":" + port + ". Is the server running?");
        }
        
        try (Socket socket = new Socket()) {
            // Set connection timeout
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
            
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            
            // Create and send operation request
            FileOperation operation = new FileOperation(
                    "DOWNLOAD", fileId, chunkNumber, 0
            );
            out.writeObject(operation);
            
            // Get assigned container ID
            String containerId = (String) in.readObject();
            
            // Read the file data
            int dataLength = in.readInt();
            if (dataLength <= 0) {
                throw new IOException("Invalid data length received: " + dataLength);
            }
            
            byte[] data = new byte[dataLength];
            in.readFully(data);
            
            System.out.println("Successfully downloaded chunk " + chunkNumber +
                    " from container " + containerId);
            return data;
            
        } catch (SocketTimeoutException e) {
            isConnected = false;
            throw new IOException("Connection timed out while downloading chunk", e);
        } catch (IOException e) {
            isConnected = false;
            throw new IOException("Error during file chunk download: " + e.getMessage(), e);
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
            System.out.println("Cannot connect to server: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if the last operation succeeded in connecting
     * @return true if the client is connected to the server
     */
    public boolean isConnected() {
        return isConnected;
    }
}
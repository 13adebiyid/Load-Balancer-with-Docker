package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with load balancer.
 */
public class LoadBalancerClient {
    private String host;
    private int port;
    private boolean isConnected;
    private Random random;
    private LoadBalancer loadBalancer;//remove---------------------------------debuggnig
    
    private static final int CONNECTION_TIMEOUT = 120000; // 2 minutes 
    
    
    /**
     * Constructor initializes the client with server details
     */
    public LoadBalancerClient(String host, int port) {
        this.host = host;
        this.port = port;
        
        //debug code
        System.out.println("Initializing LoadBalancerClient with host: " + host + ", port: " + port);

        this.isConnected = false;
        this.random = new Random();
        
        this.loadBalancer = new LoadBalancer();//remove------------------------------debugging
        //REMOVE
        loadBalancer.addContainer(new FileStorageContainer("container-1", "/storage/container1"));
        loadBalancer.addContainer(new FileStorageContainer("container-2", "/storage/container2"));
        loadBalancer.addContainer(new FileStorageContainer("container-3", "/storage/container3"));
        loadBalancer.addContainer(new FileStorageContainer("container-4", "/storage/container4"));
        
        testConnection();  // Initial connection test
    }
    
    //remove-----------------------------------------debugging
     public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }
    
    /**
     * Uploads file chunk to a storage container
     */
    public String uploadFileChunk(String fileId, int chunkNumber, byte[] data)
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
                
                FileOperation operation = new FileOperation("UPLOAD", fileId, chunkNumber, data.length);
                out.writeObject(operation);
                out.flush();
                
                String containerId = (String) in.readObject();
                if (containerId == null) {
                    throw new IOException("Server did not assign a container");
                }
                
                out.write(data);
                out.flush();
                
                boolean success = in.readBoolean();
                if (!success) {
                    throw new IOException("Server reported upload failure");
                }
                
                System.out.println("Successfully uploaded chunk " + chunkNumber +" to container " + containerId);
                return containerId;
            }
        }    }
    
    /**
     * Downloads file chunk from storage container
     */
    public byte[] downloadFileChunk(String fileId, int chunkNumber)
            throws IOException, ClassNotFoundException {
        
        System.out.println("DEBUG: Sending download request to load balancer for file ID: " + fileId + ", chunk: " + chunkNumber);
        
        if (!testConnection()) {
            throw new IOException("Cannot connect to load balancer server at " +
                    host + ":" + port);
        }
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
            socket.setSoTimeout(CONNECTION_TIMEOUT);
            
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                
                FileOperation operation = new FileOperation("DOWNLOAD", fileId, chunkNumber, 0);
                out.writeObject(operation);
                out.flush();
                
                String containerId = (String) in.readObject();
                if (containerId == null) {
                    throw new IOException("Server did not assign a container");
                }
                
                System.out.println("DEBUG: Load balancer assigned container: " + containerId);
                
                int dataLength = in.readInt();
                if (dataLength <= 0) {
                    throw new IOException("Invalid data length received: " + dataLength);
                }
                
                byte[] data = new byte[dataLength];
                in.readFully(data);
                
                
                System.out.println("DEBUG Successfully downloaded chunk " + chunkNumber +" from container " + containerId);
                return data;
            }
        }    }
    
    /**
     * Tests if server available
     */
    public boolean testConnection() {
        try (Socket socket = new Socket()) {
            System.out.println("Attempting to connect to " + host + ":" + port);
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
            isConnected = true;
            System.out.println("Successfully connected to load balancer");
            return true;
        } catch (IOException e) {
            isConnected = false;
            System.err.println("Cannot connect to server: " + e.getMessage());
            e.printStackTrace(); 
            return false;
        }
    }
    
    /**
     * @returns true if last operation succeeded connecting
     */
    public boolean isConnected() {
        return isConnected;
    }
}
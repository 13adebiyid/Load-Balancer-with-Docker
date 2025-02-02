package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.*;

/**
 * Handles network communication for the load balancer
 * This class manages incoming connections and file operation requests
 */
public class LoadBalancerServer {
    private int port;
    private LoadBalancer loadBalancer;
    private boolean running;
    
    public LoadBalancerServer(int port, LoadBalancer loadBalancer) {
        this.port = port;
        this.loadBalancer = loadBalancer;
        this.running = false;
    }
    
    public void start() {
        running = true;
        
        // Run server in a separate thread
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Load balancer listening on port " + port);
                
                while (running) {
                    // Accept client connections
                    Socket clientSocket = serverSocket.accept();
                    
                    // Handle each client in a new thread
                    new Thread(() -> handleClient(clientSocket)).start();
                }
                
            } catch (IOException e) {
                System.out.println("Server error: " + e.getMessage());
            }
        }).start();
    }
    
    private void handleClient(Socket clientSocket) {
        try (
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())
        ) {
            // Read the operation request
            FileOperation operation = (FileOperation) in.readObject();
            
            // Process the operation using load balancer
            FileStorageContainer container = loadBalancer.getNextContainer();
            
            // Send back the selected container details
            out.writeObject(container.getId());
            out.flush();
            
            // Process the actual file operation
            if (operation.getType().equals("UPLOAD")) {
                // Read file data and store it
                byte[] data = new byte[operation.getChunkSize()];
                int bytesRead = in.read(data);
                
                container.storeFileChunk(
                    operation.getFileId(),
                    operation.getChunkNumber(),
                    data,
                    1.0
                );
                
                // Send confirmation
                out.writeBoolean(true);
                
            } else if (operation.getType().equals("DOWNLOAD")) {
                // Retrieve and send file data
                byte[] data = container.retrieveFileChunk(
                    operation.getFileId(),
                    operation.getChunkNumber(),
                    1.0
                );
                
                out.writeInt(data.length);
                out.write(data);
            }
            
        } catch (Exception e) {
            System.out.println("Error handling client: " + e.getMessage());
        }
    }
    
    public void stop() {
        running = false;
    }
}
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
    private ServerSocket serverSocket;
    
    public LoadBalancerServer(int port, LoadBalancer loadBalancer) {
        this.port = port;
        this.loadBalancer = loadBalancer;
        this.running = false;
    }
    
    public void start() {
        running = true;
        
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Load balancer server started on port " + port);
                
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                }
                
            } catch (IOException e) {
                if (running) {
                    System.out.println("Server error: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void handleClient(Socket clientSocket) {
        try (
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())
        ) {
            // Read the operation request
            FileOperation operation = (FileOperation) in.readObject();
            
            // Get next container from load balancer
            FileStorageContainer container = loadBalancer.getNextContainer();
            
            // Send container ID to client
            out.writeObject(container.getId());
            out.flush();
            
            // Handle file operations
            if (operation.getType().equals("UPLOAD")) {
                byte[] data = new byte[operation.getChunkSize()];
                in.readFully(data);
                
                container.storeFileChunk(
                    operation.getFileId(),
                    operation.getChunkNumber(),
                    data,
                    1.0
                );
                
                out.writeBoolean(true);
                
            } else if (operation.getType().equals("DOWNLOAD")) {
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
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("Error stopping server: " + e.getMessage());
            }
        }
    }
}
package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles network communication for the load balancer
 * This class manages incoming connections and file operation requests
 */
public class LoadBalancerServer {
    private int port;
    private LoadBalancer loadBalancer;
    private boolean running;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private static final int MAX_THREADS = 10;
    
    public LoadBalancerServer(int port, LoadBalancer loadBalancer) {
        this.port = port;
        this.loadBalancer = loadBalancer;
        this.running = false;
        this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
    }
    
    public void start() {
        running = true;
        
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Load balancer server started on port " + port);
                
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connection accepted from: " + clientSocket.getInetAddress());
                    executorService.submit(() -> handleClient(clientSocket));
                }
                
            } catch (IOException e) {
                if (running) {
                    System.out.println("Server error: " + e.getMessage());
                }
                stop();
            }
        }).start();
    }
    
    private void handleClient(Socket clientSocket) {
        try(clientSocket){
            try (
                    ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())
                    ) {
                // Read the operation request
                FileOperation operation = (FileOperation) in.readObject();
                System.out.println("Received operation: " + operation.getType() + " for file: " + operation.getFileId());
                
                // Get next container from load balancer
                FileStorageContainer container = loadBalancer.getNextContainer();
                if (container == null) {
                    out.writeObject("ERROR");
                    out.writeBoolean(false);
                    return;
                }
                
                // Get current traffic level from load balancer
                double trafficMultiplier = loadBalancer.getCurrentTrafficLevel();
                System.out.println("Current traffic multiplier: " + trafficMultiplier);
                
                // Send container ID to client
                out.writeObject(container.getId());
                out.flush();
                
                // Handle file operations
                if (operation.getType().equals("UPLOAD")) {
                    handleUpload(operation, container, in, out, trafficMultiplier);
                } else if (operation.getType().equals("DOWNLOAD")) {
                    handleDownload(operation, container, in, out, trafficMultiplier);
                }
            }
            
        } catch (Exception e) {
            System.out.println("Error handling client: " + e.getMessage());
            e.printStackTrace();  // Add stack trace for better debugging
        }
    }
    
    private void handleUpload(FileOperation operation, FileStorageContainer container,
            ObjectInputStream in, ObjectOutputStream out,
            double trafficMultiplier) throws IOException, ClassNotFoundException {
        try {
            byte[] data = new byte[operation.getChunkSize()];
            in.readFully(data);
            
            container.storeFileChunk(operation.getFileId(), operation.getChunkNumber(), data, trafficMultiplier);
            
            out.writeBoolean(true);
            out.flush();  // Ensure data is sent
            
            System.out.println("Successfully stored chunk " + operation.getChunkNumber() +
                    " with traffic multiplier " + trafficMultiplier);
        } catch (Exception e) {
            try {
                out.writeBoolean(false);
                out.flush();
                
            } catch (Exception ex) {
                Logger.getLogger(LoadBalancerServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void handleDownload(FileOperation operation, FileStorageContainer container,
            ObjectInputStream in, ObjectOutputStream out,
            double trafficMultiplier) throws IOException, ClassNotFoundException {
        try {
            byte[] data = container.retrieveFileChunk(operation.getFileId(), operation.getChunkNumber(), trafficMultiplier);
            
            out.writeInt(data.length);
            out.write(data);
            out.flush();
            
            System.out.println("Successfully retrieved chunk " + operation.getChunkNumber() +
                    " with traffic multiplier " + trafficMultiplier);
        } catch (Exception e) {
            try {
                out.writeInt(-1);  // Indicate error
                out.flush();
                
            } catch (Exception ex) {
                Logger.getLogger(LoadBalancerServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("Error stopping server: " + e.getMessage());
            }
        }
        
        // Properly shut down the executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
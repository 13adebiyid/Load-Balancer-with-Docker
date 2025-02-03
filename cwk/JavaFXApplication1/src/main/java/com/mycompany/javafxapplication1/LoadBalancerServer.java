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
        // Create a thread pool to handle multiple client connections efficiently
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
        // Set socket timeout to prevent hanging connections
        try {
            clientSocket.setSoTimeout(30000);  // 30 second timeout
        } catch (SocketException e) {
            System.err.println("Failed to set socket timeout: " + e.getMessage());
        }
        
        // Use try-with-resources to ensure proper resource cleanup
        try (clientSocket;
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
            
            // Send ready signal to client
            out.writeObject("READY");
            out.flush();
            
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
            
            // Handle specific operations
            if (operation.getType().equals("UPLOAD")) {
                handleUpload(operation, container, in, out, trafficMultiplier);
            } else if (operation.getType().equals("DOWNLOAD")) {
                handleDownload(operation, container, in, out, trafficMultiplier);
            }
            
        } catch (EOFException e) {
            System.out.println("Client disconnected: " + clientSocket.getInetAddress());
        } catch (SocketTimeoutException e) {
            System.err.println("Connection timed out: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleUpload(FileOperation operation, FileStorageContainer container,
            ObjectInputStream in, ObjectOutputStream out,
            double trafficMultiplier) throws IOException {
        try {
            // Read the file data length and then the data itself
            int dataLength = operation.getChunkSize();
            byte[] data = new byte[dataLength];
            in.readFully(data);
            
            // Store the chunk in the container
            container.storeFileChunk(
                    operation.getFileId(),
                    operation.getChunkNumber(),
                    data,
                    trafficMultiplier
            );
            
            // Send success confirmation
            out.writeBoolean(true);
            out.flush();
            
            System.out.println("Successfully stored chunk " + operation.getChunkNumber() +
                    " with traffic multiplier " + trafficMultiplier);
        } catch (Exception e) {
            out.writeBoolean(false);
            out.flush();
            throw new IOException("Upload failed", e);
        }
    }
    
    private void handleDownload(FileOperation operation, FileStorageContainer container,
            ObjectInputStream in, ObjectOutputStream out,
            double trafficMultiplier) throws IOException {
        try {
            // Retrieve the chunk from the container
            byte[] data = container.retrieveFileChunk(
                    operation.getFileId(),
                    operation.getChunkNumber(),
                    trafficMultiplier
            );
            
            // Send the data length and chunk data
            out.writeInt(data.length);
            out.write(data);
            out.flush();
            
            System.out.println("Successfully retrieved chunk " + operation.getChunkNumber() +
                    " with traffic multiplier " + trafficMultiplier);
        } catch (Exception e) {
            out.writeInt(-1);  // Indicate error
            out.flush();
            throw new IOException("Download failed", e);
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
        
        // Graceful shutdown of the executor service
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
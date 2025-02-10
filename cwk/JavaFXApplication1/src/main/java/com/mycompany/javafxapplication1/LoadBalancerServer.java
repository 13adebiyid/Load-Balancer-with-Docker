package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadBalancerServer {
    private int port;
    private LoadBalancer loadBalancer;
    private boolean running;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private static final int MAX_THREADS = 10;
    private static final int SOCKET_TIMEOUT = 120000; // 2 minutes to account for artificial delays
    
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
                    System.out.println("New client connection accepted from: " +
                            clientSocket.getInetAddress());
                    executorService.submit(() -> handleClient(clientSocket));
                }
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("Server error: " + e.getMessage());
                }
                stop();
            }
        }).start();
    }
    
    private void handleClient(Socket clientSocket) {
        // Set socket timeout to prevent hanging connections
        try {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT);
        } catch (SocketException e) {
            System.err.println("Failed to set socket timeout: " + e.getMessage());
        }
        
        try (clientSocket;
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
            
            // Read the operation request
            FileOperation operation = (FileOperation) in.readObject();
            System.out.println("Received operation: " + operation.getType() +
                    " for file: " + operation.getFileId());
            
            // Get next container from load balancer
            FileStorageContainer container = loadBalancer.getNextContainer();
            if (container == null) {
                sendErrorResponse(out, "No available containers");
                return;
            }
            
            
            // Send container ID to client
            out.writeObject(container.getId());
            out.flush();
            
            // Handle specific operations
            switch (operation.getType().toUpperCase()) {
                case "UPLOAD":
                    handleUpload(operation, container, in, out);
                    break;
                case "DOWNLOAD":
                    handleDownload(operation, container, in, out);
                    break;
                default:
                    sendErrorResponse(out, "Unknown operation type: " + operation.getType());
            }
            //debug code
            System.out.println("Connection from: " + clientSocket.getInetAddress());
            System.out.println("Using container: " + container.getId());

            
        } catch (EOFException e) {
            System.out.println("Client disconnected normally: " + clientSocket.getInetAddress());
        } catch (SocketTimeoutException e) {
            System.err.println("Connection timed out: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        }
        
    }
    
    private void handleUpload(FileOperation operation, FileStorageContainer container,
            ObjectInputStream in, ObjectOutputStream out)
            throws IOException {
        try {
            // Read the file data
            
            //debug code
            System.out.println("Handling upload for file: " + operation.getFileId() +
                    ", chunk: " + operation.getChunkNumber() +
                    " to container: " + container.getId());
            
            int dataLength = operation.getChunkSize();
            if (dataLength <= 0) {
                sendErrorResponse(out, "Invalid chunk size");
                return;
            }
            
            byte[] data = new byte[dataLength];
            in.readFully(data);
            
            // Store the chunk in the container
            container.storeFileChunk(
                    operation.getFileId(),
                    operation.getChunkNumber(),
                    data
            );
            
            // Send success confirmation
            out.writeBoolean(true);
            out.flush();
            
            System.out.println("Successfully stored chunk " + operation.getChunkNumber());
         
            
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            sendErrorResponse(out, "Upload failed: " + e.getMessage());
            throw new IOException("Upload failed", e);
        }
    }
    
    private void handleDownload(FileOperation operation, FileStorageContainer container, ObjectInputStream in, ObjectOutputStream out)
            throws IOException {
        try {
            
            //debug code
            System.out.println("Handling upload for file: " + operation.getFileId() +
                    ", chunk: " + operation.getChunkNumber() +
                    " to container: " + container.getId());
            
            // Retrieve the chunk from the container
            byte[] data = container.retrieveFileChunk(
                    operation.getFileId(),
                    operation.getChunkNumber()
            );
            
            if (data == null) {
                sendErrorResponse(out, "Failed to retrieve chunk data");
                return;
            }
            
            // Send the data length and chunk data
            out.writeInt(data.length);
            out.write(data);
            out.flush();
            
            System.out.println("Successfully retrieved chunk " + operation.getChunkNumber()+
                    " (size: " + data.length + " bytes)");
            
        } catch (Exception e) {
            System.err.println("Download failed: " + e.getMessage());
            sendErrorResponse(out, "Download failed: " + e.getMessage());
            throw new IOException("Download failed", e);
        }
    }
    
    private void sendErrorResponse(ObjectOutputStream out, String message) {
        try {
            out.writeInt(-1);  // Error indicator
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send error response: " + e.getMessage());
        }
    }
    
    public void stop() {
        running = false;
        
        // Close the server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error stopping server: " + e.getMessage());
            }
        }
        
        // Shutdown the executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Load balancer server stopped");
    }
}
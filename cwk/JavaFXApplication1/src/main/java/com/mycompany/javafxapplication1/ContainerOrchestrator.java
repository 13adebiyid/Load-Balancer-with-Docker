package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContainerOrchestrator {
    private final ServerSocket serverSocket;
    private final DockerAPI dockerAPI;
    private final ExecutorService executorService;
    private volatile boolean running;
    
    public ContainerOrchestrator() throws IOException {
        this.serverSocket = new ServerSocket(8081);
        this.dockerAPI = new DockerAPI();
        this.executorService = Executors.newFixedThreadPool(10);
        this.running = true;
    }
    
    public void start() {
        System.out.println("Container Orchestrator started on port 8081");
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }
    
    private void handleClient(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            // Read the request line
            String requestLine = reader.readLine();
            if (requestLine == null) return;
            
            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];
            String path = requestParts[1];
            
            // Read headers until empty line
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }
            
            // Handle different endpoints
            if (path.equals("/scale") && method.equals("POST")) {
                // Read the request body
                char[] body = new char[contentLength];
                reader.read(body, 0, contentLength);
                String response = dockerAPI.scaleService(new ByteArrayInputStream(new String(body).getBytes()));
                
                // Send response
                writer.println("HTTP/1.1 200 OK");
                writer.println("Content-Type: application/json");
                writer.println("Content-Length: " + response.length());
                writer.println();
                writer.println(response);
                
            } else if (path.equals("/containers/count") && method.equals("GET")) {
                String response = dockerAPI.getContainerCount();
                
                writer.println("HTTP/1.1 200 OK");
                writer.println("Content-Type: application/json");
                writer.println("Content-Length: " + response.length());
                writer.println();
                writer.println(response);
                
            } else {
                // Method not allowed or path not found
                writer.println("HTTP/1.1 405 Method Not Allowed");
                writer.println();
            }
            
        } catch (IOException e) {
            System.err.println("Error handling client request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
    
    public void stop() {
        running = false;
        executorService.shutdown();
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
    }
}
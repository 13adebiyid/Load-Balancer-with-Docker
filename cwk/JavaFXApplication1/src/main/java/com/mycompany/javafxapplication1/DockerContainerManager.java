package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DockerContainerManager {
    protected static final int MIN_CONTAINERS = 4;
    protected static final int MAX_CONTAINERS = 10;
    private static final String HOST_IP = "host.docker.internal";
    private static final int HOST_PORT = 8081;
    private final ExecutorService executorService;
    
    public DockerContainerManager() {
        // Create a thread pool for async operations
        this.executorService = Executors.newFixedThreadPool(5);
    }
    
    public void scaleContainers(int targetCount) {
        try {
            // Create final copy of targetCount for lambda
            final int finalTargetCount = Math.min(Math.max(targetCount, MIN_CONTAINERS), MAX_CONTAINERS);
            
            String requestBody = String.format(
                    "{\"service\":\"comp20081-files-container\",\"targetCount\":%d}",
                    finalTargetCount
            );
            
            executorService.submit(() -> {
                try (Socket socket = new Socket(HOST_IP, HOST_PORT);
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    
                    // Send HTTP POST request
                    out.println("POST /scale HTTP/1.1");
                    out.println("Host: " + HOST_IP);
                    out.println("Content-Type: application/json");
                    out.println("Content-Length: " + requestBody.length());
                    out.println();
                    out.println(requestBody);
                    
                    // Read response
                    String responseLine = in.readLine();
                    if (responseLine != null && responseLine.contains("200")) {
                        System.out.println("Successfully scaled containers to: " + finalTargetCount);
                        waitForContainers(finalTargetCount);
                    } else {
                        System.err.println("Failed to scale containers: " + responseLine);
                    }
                } catch (Exception e) {
                    System.err.println("Error during scaling request: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Error initiating scaling operation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Get the current number of containers
    public int getCurrentContainerCount() {
        try (Socket socket = new Socket(HOST_IP, HOST_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            // Send HTTP GET request
            out.println("GET /containers/count HTTP/1.1");
            out.println("Host: " + HOST_IP);
            out.println();
            
            // Read response
            String line;
            boolean headersDone = false;
            StringBuilder response = new StringBuilder();
            
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    headersDone = true;
                    continue;
                }
                if (headersDone) {
                    response.append(line);
                }
            }
            
            // Parse response
            String jsonResponse = response.toString();
            if (jsonResponse.contains("count")) {
                return Integer.parseInt(
                        jsonResponse.split(":")[1].replace("}", "").trim()
                );
            }
        } catch (Exception e) {
            System.err.println("Error getting container count: " + e.getMessage());
        }
        return -1;
    }
    
    private void waitForContainers(int expectedCount) {
        try {
            int attempts = 10;
            while (attempts-- > 0) {
                int runningContainers = getCurrentContainerCount();
                if (runningContainers >= expectedCount) {
                    System.out.println("All storage containers are up and running.");
                    return;
                }
                System.out.println("Waiting for containers to start... (" +
                        runningContainers + "/" + expectedCount + ")");
                Thread.sleep(5000);
            }
            System.err.println("Timeout waiting for containers to start.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for containers");
        }
    }
    
    // Cleanup method to shut down the executor service
    public void shutdown() {
        executorService.shutdown();
    }
}
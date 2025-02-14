package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DockerContainerManager {
    protected static final int MIN_CONTAINERS = 4;
    protected static final int MAX_CONTAINERS = 10;
    private static final String ORCHESTRATOR_HOST = "host.docker.internal";
    private static final int ORCHESTRATOR_PORT = 8081;
    private final ExecutorService executorService;
    
    public DockerContainerManager() {
        // Create a thread pool for async operations
        this.executorService = Executors.newFixedThreadPool(5);
    }
    
    public void scaleContainers(int targetCount) {
        try {
            final int finalTargetCount = Math.min(Math.max(targetCount, MIN_CONTAINERS), MAX_CONTAINERS);
            
            String requestBody = String.format(
                "{\"service\":\"comp20081-files-container\",\"targetCount\":%d}",
                finalTargetCount
            );

            // Use HttpURLConnection instead of Socket for better HTTP handling
            URL url = new URL("http://" + ORCHESTRATOR_HOST + ":" + ORCHESTRATOR_PORT + "/scale");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    System.out.println("Scale response: " + response.toString());
                }
            } else {
                System.err.println("Scale request failed with code: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("Error during scaling operation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Get the current number of containers
    public int getCurrentContainerCount() {
        try {
            URL url = new URL("http://" + ORCHESTRATOR_HOST + ":" + ORCHESTRATOR_PORT + "/containers/count");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String response = br.readLine();
                    // Parse JSON response
                    if (response != null && response.contains("count")) {
                        return Integer.parseInt(
                            response.split(":")[1].replace("}", "").trim()
                        );
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting container count: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }
    
//    private void waitForContainers(int expectedCount) {
//        try {
//            int attempts = 10;
//            while (attempts-- > 0) {
//                int runningContainers = getCurrentContainerCount();
//                if (runningContainers >= expectedCount) {
//                    System.out.println("All storage containers are up and running.");
//                    return;
//                }
//                System.out.println("Waiting for containers to start... (" +
//                        runningContainers + "/" + expectedCount + ")");
//                Thread.sleep(5000);
//            }
//            System.err.println("Timeout waiting for containers to start.");
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            System.err.println("Interrupted while waiting for containers");
//        }
//    }
//    
    // Cleanup method to shut down the executor service
    public void shutdown() {
        executorService.shutdown();
    }
}
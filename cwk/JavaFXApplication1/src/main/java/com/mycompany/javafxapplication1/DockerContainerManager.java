package com.mycompany.javafxapplication1;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DockerContainerManager {
    private static final String DOCKER_COMPOSE_FILE = "docker-compose.yml";
    private static final int MIN_CONTAINERS = 4;
    private static final int MAX_CONTAINERS = 10;
    private static final String STORAGE_SERVICE_NAME = "comp20081-files-container";
    
    
    
    
    public void scaleContainers(int targetCount) {
        try {
            targetCount = Math.min(Math.max(targetCount, MIN_CONTAINERS), MAX_CONTAINERS);
            
            String[] command = {
                "docker-compose",
                "-f", DOCKER_COMPOSE_FILE,
                "up",
                "--scale", STORAGE_SERVICE_NAME + "=" + targetCount,
                "-d"
            };
            
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            
            if (process.exitValue() == 0) {
                System.out.println("Successfully scaled containers to: " + targetCount);
                waitForContainers(targetCount);
            } else {
                System.err.println("Failed to scale containers. Exit code: " + process.exitValue());
            }
            
        } catch (Exception e) {
            System.err.println("Error scaling containers: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void waitForContainers(int expectedCount) throws InterruptedException, IOException {
        int attempts = 10;
        while (attempts-- > 0) {
            int runningContainers = getCurrentContainerCount();
            if (runningContainers >= expectedCount) {
                System.out.println("All storage containers are up and running.");
                return;
            }
            System.out.println("Waiting for containers to start... (" + runningContainers + "/" + expectedCount + ")");
            Thread.sleep(5000);
        }
        System.err.println("Timeout waiting for containers to start.");
    }
    
    public int getCurrentContainerCount() {
        try {
            Process process = Runtime.getRuntime().exec(
                    "docker ps --filter 'name=" + STORAGE_SERVICE_NAME + "' --format '{{.Names}}'");
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                List<String> containerNames = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        containerNames.add(line.trim());
                    }
                }
                return containerNames.size();
            }
        } catch (Exception e) {
            System.err.println("Error getting container count: " + e.getMessage());
            return -1;
        }
    }
}

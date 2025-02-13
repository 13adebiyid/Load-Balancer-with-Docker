/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ntu-user
 */
public class DockerContainerManager {
    private static final String DOCKER_COMPOSE_FILE = "docker-compose.yml";
    private static final int MIN_CONTAINERS = 4;
    private static final int MAX_CONTAINERS = 10;
    private static final String STORAGE_SERVICE_NAME = "file_storage";
    
    public void scaleContainers(int targetCount) {
        try {
            // Ensure target count is within bounds
            targetCount = Math.min(Math.max(targetCount, MIN_CONTAINERS), MAX_CONTAINERS);
            
            String[] command = {
                "docker-compose",
                "-f", DOCKER_COMPOSE_FILE,
                "up",
                "--scale", STORAGE_SERVICE_NAME + "=" + targetCount,
                "-d"
            };
            
            Process process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Docker: " + line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Successfully scaled containers to: " + targetCount);
            } else {
                System.err.println("Failed to scale containers. Exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            System.err.println("Error scaling containers: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public int getCurrentContainerCount() {
        try {
            Process process = Runtime.getRuntime().exec(
                "docker-compose -f " + DOCKER_COMPOSE_FILE + " ps -q " + STORAGE_SERVICE_NAME
            );
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                List<String> containerIds = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        containerIds.add(line.trim());
                    }
                }
                return containerIds.size();
            }
        } catch (Exception e) {
            System.err.println("Error getting container count: " + e.getMessage());
            return -1;
        }
    }
}
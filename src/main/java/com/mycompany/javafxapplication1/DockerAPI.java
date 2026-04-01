package com.mycompany.javafxapplication1;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class DockerAPI {
    public String scaleService(InputStream requestBody) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            
            int targetCount = Integer.parseInt(
                    json.toString().split("targetCount\":")[1].split("}")[0].trim()
            );
            
            ProcessBuilder pb = new ProcessBuilder("docker-compose","-f", "/app/docker-compose.yml","up","-d","--scale", "comp20081-files-container=" + targetCount);
            
            Process process = pb.start();
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            
            if (completed && process.exitValue() == 0) {
                return "{\"status\":\"success\",\"message\":\"Scaled to " + targetCount + " containers\"}";
            } else {
                return "{\"status\":\"error\",\"message\":\"Scaling operation failed\"}";
            }
            
        } catch (Exception e) {
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
    
    public String getContainerCount() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker","ps","--filter", "name=comp20081-files-container","--format", "{{.Names}}");
            
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );
            
            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            
            return "{\"count\":" + count + "}";
            
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
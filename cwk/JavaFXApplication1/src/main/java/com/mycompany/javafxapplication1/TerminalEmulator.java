package com.mycompany.javafxapplication1;

import java.io.*;
import java.util.*;

public class TerminalEmulator {
    private String currentDirectory;
    
    public TerminalEmulator() {
        currentDirectory = System.getProperty("user.home");
    }
    
    public String executeCommand(String command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // Set up the command
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                // For Linux/Unix systems
                processBuilder.command("bash", "-c", command);
            }
            
            // Set working directory
            processBuilder.directory(new File(currentDirectory));
            
            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true);
            
            // Start the process
            Process process = processBuilder.start();
            
            // Read the output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait for the process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "Command failed with exit code: " + exitCode + "\n" + output.toString();
            }
            
            return output.toString();
            
        } catch (IOException | InterruptedException e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}
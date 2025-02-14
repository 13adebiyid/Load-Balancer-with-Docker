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
            
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("bash", "-c", command);
            }
            
            processBuilder.directory(new File(currentDirectory));
            
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait fucntion as process is not immediate
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
package com.mycompany.javafxapplication1;

import com.jcraft.jsch.*;
import java.io.*;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Handles SSH connections to containers using JSch library
 */
public class ContainerSSH {
    private static final Logger logger = Logger.getLogger(ContainerSSH.class.getName());
    private JSch jsch;
    private Session session;
    private ChannelShell channel;
    private OutputStream commander;         // Changed to OutputStream for sending commands
    private ByteArrayOutputStream responseStream;
    
    public ContainerSSH() {
        jsch = new JSch();
        responseStream = new ByteArrayOutputStream();
    }
    
    public void connect(String containerId, String username, String password) throws JSchException {
        try {
            // Close existing connections if any
            disconnect();
            
            // Map container ID to its Docker network alias
            String host = containerId.replace("container-", "storage");
            logger.info("Connecting to " + host + " as " + username);
            
            // Create new SSH session
            session = jsch.getSession(username, host, 22);
            session.setPassword(password);
            
            // Don't check host key for development environment
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            // Connect with timeout
            session.connect(5000);
            
            // Create and connect shell channel
            channel = (ChannelShell) session.openChannel("shell");
            
            // Set up input/output streams
            responseStream = new ByteArrayOutputStream();
            channel.setOutputStream(responseStream);
            try {
                commander = channel.getOutputStream();  // Get output stream for sending commands
            } catch (IOException ex) {
                Logger.getLogger(ContainerSSH.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            // Connect the channel
            channel.connect(3000);
            
            logger.info("Successfully connected to " + host);
            
        } catch (JSchException e) {
            disconnect(); // Clean up on failure
            logger.severe("Failed to connect to " + containerId + ": " + e.getMessage());
            throw e;
        }
    }
    
    public void disconnect() {
        try {
            if (commander != null) {
                commander.close();
                commander = null;
            }
        } catch (IOException e) {
            logger.warning("Error closing command stream: " + e.getMessage());
        }
        
        if (channel != null) {
            channel.disconnect();
            channel = null;
        }
        
        if (session != null) {
            session.disconnect();
            session = null;
        }
        
        responseStream.reset();
        logger.info("Disconnected from container");
    }
    
    public String executeCommand(String command) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to container");
        }
        
        try {
            // Send command with newline
            commander.write((command + "\n").getBytes());
            commander.flush();
            
            // Wait for response
            Thread.sleep(1000);  // Give more time for command execution
            
            // Get and process response
            String response = responseStream.toString();
            responseStream.reset();
            
            // Clean up the response by removing command echo and prompt
            String[] lines = response.split("\n");
            StringBuilder cleanResponse = new StringBuilder();
            for (String line : lines) {
                // Skip the command echo and prompt lines
                if (!line.trim().equals(command) && !line.matches(".*[@].*[#$]\\s*$")) {
                    cleanResponse.append(line).append("\n");
                }
            }
            
            return cleanResponse.toString();
            
        } catch (Exception e) {
            throw new IOException("Command execution failed: " + e.getMessage());
        }
    }
    
    public String getSystemInfo() throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to container");
        }
        
        StringBuilder info = new StringBuilder();
        info.append("=== System Information ===\n");
        info.append(executeCommand("uname -a")).append("\n");
        info.append("=== Memory Usage ===\n");
        info.append(executeCommand("free -h")).append("\n");
        info.append("=== Disk Usage ===\n");
        info.append(executeCommand("df -h")).append("\n");
        info.append("=== Process List ===\n");
        info.append(executeCommand("ps aux | head -n 5"));
        return info.toString();
    }
    
    public boolean isConnected() {
        return session != null && session.isConnected() &&
                channel != null && channel.isConnected() &&
                commander != null;
    }
}
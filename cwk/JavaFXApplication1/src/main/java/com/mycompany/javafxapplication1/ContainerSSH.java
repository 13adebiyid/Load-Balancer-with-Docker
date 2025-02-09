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
    private PrintStream commander;
    private ByteArrayOutputStream responseStream;
    
    public ContainerSSH() {
        jsch = new JSch();
        responseStream = new ByteArrayOutputStream();
    }
    
    /**
     * Connects to a container via SSH
     */
    public void connect(String containerId, String username, String password) throws JSchException {
        // Get container host and port from configuration or environment
        String host = "localhost"; // Default for local testing
        int port = 22;
        
        // Create SSH session
        session = jsch.getSession(username, host, port);
        session.setPassword(password);
        
        // Skip host key checking (not recommended for production)
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        
        try {
            // Connect to the container
            session.connect(30000);
            logger.info("SSH session established to container: " + containerId);
            
            // Open shell channel
            channel = (ChannelShell)session.openChannel("shell");
            channel.setPtyType("dumb");
            
            try {
                // Set up streams
                commander = new PrintStream(channel.getOutputStream());
            } catch (IOException ex) {
                Logger.getLogger(ContainerSSH.class.getName()).log(Level.SEVERE, null, ex);
            }
            channel.setOutputStream(responseStream);
            
            // Connect channel
            channel.connect(3000);
            
        } catch (JSchException e) {
            disconnect();
            throw e;
        }
    }
    
    /**
     * Executes a command in the container
     */
    public String executeCommand(String command) throws IOException {
        if (channel == null || !channel.isConnected()) {
            throw new IOException("Not connected to container");
        }
        
        try {
            // Clear previous response
            responseStream.reset();
            
            // Send command
            commander.println(command);
            commander.flush();
            
            // Wait for response
            Thread.sleep(1000);
            
            // Get response
            String response = responseStream.toString();
            logger.info("Command executed: " + command + "\nResponse: " + response);
            
            return response;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error executing command: " + command, e);
            throw new IOException("Command execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Creates a new file in the container
     */
    public void createFile(String filename, String content) throws IOException {
        String escapedContent = content.replace("\"", "\\\"");
        String command = String.format("echo \"%s\" > %s", escapedContent, filename);
        executeCommand(command);
    }
    
    /**
     * Reads a file from the container
     */
    public String readFile(String filename) throws IOException {
        return executeCommand("cat " + filename);
    }
    
    /**
     * Lists directory contents in the container
     */
    public String listDirectory(String path) throws IOException {
        return executeCommand("ls -la " + path);
    }
    
    /**
     * Gets system information from the container
     */
    public String getSystemInfo() throws IOException {
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
    
    /**
     * Disconnects from the container
     */
    public void disconnect() {
        if (commander != null) {
            commander.close();
        }
        
        if (channel != null) {
            channel.disconnect();
        }
        
        if (session != null) {
            session.disconnect();
        }
        
        logger.info("Disconnected from container");
    }
    
    /**
     * Checks if connected to container
     */
    public boolean isConnected() {
        return session != null && session.isConnected() && 
               channel != null && channel.isConnected();
    }
}
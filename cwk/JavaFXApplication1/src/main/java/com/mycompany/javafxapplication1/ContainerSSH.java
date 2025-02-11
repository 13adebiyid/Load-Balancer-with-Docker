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
        try {
            jsch = new JSch();
            
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
            logger.info("Successfully connected to " + host);
            
        } catch (JSchException e) {
            logger.severe("Failed to connect to " + containerId + ": " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Executes a command in the container
     */
    public String executeCommand(String command) throws IOException {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec)session.openChannel("exec");
            
            // Create streams for output
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            channel.setOutputStream(outputStream);
            
            // Execute command
            channel.setCommand(command);
            channel.connect();
            
            // Wait for command to complete
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }
            
            return outputStream.toString();
            
        } catch (Exception e) {
            throw new IOException("Command execution failed: " + e.getMessage());
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }
    
    /**
     * Gets system information from the container and formats info for easier read 
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
     * Disconnects from container
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
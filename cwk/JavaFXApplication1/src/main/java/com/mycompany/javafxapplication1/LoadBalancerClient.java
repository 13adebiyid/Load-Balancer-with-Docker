package com.mycompany.javafxapplication1;

import java.io.*;
import java.net.*;

/**
 * Client class for communicating with the load balancer
 * Used by the GUI to send file operations to the load balancer
 */
public class LoadBalancerClient {
    private String host;
    private int port;
    
    public LoadBalancerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Uploads a file chunk to a storage container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The chunk sequence number
     * @param data The file chunk data
     * @return The ID of the container that stored the chunk
     */
    public String uploadFileChunk(String fileId, int chunkNumber, byte[] data) 
            throws IOException, ClassNotFoundException {
        
        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            
            // Create and send operation request
            FileOperation operation = new FileOperation(
                "UPLOAD", fileId, chunkNumber, data.length
            );
            out.writeObject(operation);
            
            // Get assigned container ID
            String containerId = (String) in.readObject();
            
            // Send the actual file data
            out.write(data);
            out.flush();
            
            // Wait for confirmation
            boolean success = in.readBoolean();
            if (!success) {
                throw new IOException("Upload failed");
            }
            
            return containerId;
        }
    }
    
    /**
     * Downloads a file chunk from a storage container
     * @param fileId Unique identifier for the file
     * @param chunkNumber The chunk sequence number
     * @return The file chunk data
     */
    public byte[] downloadFileChunk(String fileId, int chunkNumber) 
            throws IOException, ClassNotFoundException {
        
        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            
            // Create and send operation request
            FileOperation operation = new FileOperation(
                "DOWNLOAD", fileId, chunkNumber, 0
            );
            out.writeObject(operation);
            
            // Get assigned container ID (we might need this later)
            String containerId = (String) in.readObject();
            
            // Read the file data
            int dataLength = in.readInt();
            byte[] data = new byte[dataLength];
            in.readFully(data);
            
            return data;
        }
    }
}
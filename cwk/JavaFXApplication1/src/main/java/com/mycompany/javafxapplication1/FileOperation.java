package com.mycompany.javafxapplication1;

import java.io.Serializable;

/**
 * Represents a file operation to be processed by the load balancer
 * This class is serializable so it can be sent over the network
 */
public class FileOperation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String type;        // "UPLOAD" or "DOWNLOAD"
    private String fileId;      // Unique identifier for the file
    private int chunkNumber;    // Sequence number of the chunk
    private int chunkSize;      // Size of the chunk in bytes
    
    public FileOperation(String type, String fileId, int chunkNumber, int chunkSize) {
        this.type = type;
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.chunkSize = chunkSize;
    }
    
    // Standard getters
    public String getType() { return type; }
    public String getFileId() { return fileId; }
    public int getChunkNumber() { return chunkNumber; }
    public int getChunkSize() { return chunkSize; }
}
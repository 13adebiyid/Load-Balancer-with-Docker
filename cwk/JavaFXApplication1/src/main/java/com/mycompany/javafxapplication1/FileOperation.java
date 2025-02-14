package com.mycompany.javafxapplication1;

import java.io.Serializable;

/**
 * file operations to be processed by the load balancer
 */
public class FileOperation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String type;        
    private String fileId;      
    private int chunkNumber;    
    private int chunkSize;      
    
    public FileOperation(String type, String fileId, int chunkNumber, int chunkSize) {
        this.type = type;
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.chunkSize = chunkSize;
    }
    
    public String getType() { return type; }
    public String getFileId() { return fileId; }
    public int getChunkNumber() { return chunkNumber; }
    public int getChunkSize() { return chunkSize; }
}
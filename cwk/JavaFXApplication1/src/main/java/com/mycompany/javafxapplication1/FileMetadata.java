package com.mycompany.javafxapplication1;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks metadata about files stored in the distributed system
 * This includes information about where chunks are stored and file permissions
 */
public class FileMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String fileId;          // Unique identifier for the file
    private String fileName;        // Original file name
    private String ownerUser;       // User who owns the file
    private long totalSize;         // Total file size in bytes
    private int totalChunks;        // Number of chunks this file was split into
    private List<ChunkLocation> chunkLocations;  // Where each chunk is stored
    private boolean isShared;       // Whether file is shared with others
    
    public FileMetadata(String fileId, String fileName, String ownerUser, long totalSize) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.ownerUser = ownerUser;
        this.totalSize = totalSize;
        this.chunkLocations = new ArrayList<>();
        this.isShared = false;
    }
    
    // Inner class to track chunk locations
    public static class ChunkLocation implements Serializable {
        private static final long serialVersionUID = 1L;
        private int chunkNumber;
        private String containerId;
        
        public ChunkLocation(int chunkNumber, String containerId) {
            this.chunkNumber = chunkNumber;
            this.containerId = containerId;
        }
        
        public int getChunkNumber() { return chunkNumber; }
        public String getContainerId() { return containerId; }
    }
    
    // Add information about where a chunk is stored
    public void addChunkLocation(int chunkNumber, String containerId) {
        chunkLocations.add(new ChunkLocation(chunkNumber, containerId));
        totalChunks = Math.max(totalChunks, chunkNumber + 1);
    }
    
    // Get the container ID for a specific chunk
    public String getContainerForChunk(int chunkNumber) {
        return chunkLocations.stream()
            .filter(loc -> loc.getChunkNumber() == chunkNumber)
            .map(ChunkLocation::getContainerId)
            .findFirst()
            .orElse(null);
    }
    
    // Getters and setters
    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public String getOwnerUser() { return ownerUser; }
    public long getTotalSize() { return totalSize; }
    public int getTotalChunks() { return totalChunks; }
    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { this.isShared = shared; }
}
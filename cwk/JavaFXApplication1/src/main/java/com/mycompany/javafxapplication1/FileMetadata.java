package com.mycompany.javafxapplication1;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks metadata about files stored 
 */
public class FileMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String fileId;          
    private String fileName;        
    private String ownerUser;      
    private long totalSize;         
    private int totalChunks;        
    private List<ChunkLocation> chunkLocations;  
    private boolean isShared;       
    
    public FileMetadata(String fileId, String fileName, String ownerUser, long totalSize) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.ownerUser = ownerUser;
        this.totalSize = totalSize;
        this.chunkLocations = new ArrayList<>();
        this.isShared = false;
    }
    
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
    
    public void addChunkLocation(int chunkNumber, String containerId) {
        chunkLocations.add(new ChunkLocation(chunkNumber, containerId));
        totalChunks = Math.max(totalChunks, chunkNumber + 1);
    }
    
    public void setPermissions(String userName, boolean canRead, boolean canWrite, String grantedBy)
            throws ClassNotFoundException {
        DB database = new DB();
        database.setFilePermissions(this.fileId, userName, canRead, canWrite, grantedBy);
    }
    
    public String getContainerForChunk(int chunkNumber) {
        return chunkLocations.stream()
                .filter(loc -> loc.getChunkNumber() == chunkNumber)
                .map(ChunkLocation::getContainerId)
                .findFirst()
                .orElse(null);
    }
    
    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }
    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }
    
    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public String getOwnerUser() { return ownerUser; }
    public long getTotalSize() { return totalSize; }
    public int getTotalChunks() { return totalChunks; }
    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { this.isShared = shared; }
}
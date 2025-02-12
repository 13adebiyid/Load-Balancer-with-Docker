/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

/**
 *
 * @author ntu-user
 */
public class Request {
    private final String fileId;
    private final int chunkNumber;
    private final String operationType;
    private final long creationTime;
    private int priority;
    private final String userId;
    
    public Request(String fileId, int chunkNumber, String operationType, String userId) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.operationType = operationType;
        this.creationTime = System.currentTimeMillis();
        this.priority = 0;  // Initial priority
        this.userId = userId;
    }
    
    // Age the request - increase priority based on wait time
    public void age() {
        long waitTime = System.currentTimeMillis() - creationTime;
        // Increase priority by 1 for every 30 seconds of waiting
        this.priority = (int)(waitTime / 30000);
    }
    
    // Getters
    public String getFileId() { return fileId; }
    public int getChunkNumber() { return chunkNumber; }
    public String getOperationType() { return operationType; }
    public int getPriority() { return priority; }
    public String getUserId() { return userId; }
    public long getWaitTime() { 
        return System.currentTimeMillis() - creationTime; 
    }
}

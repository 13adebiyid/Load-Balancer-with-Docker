/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author ntu-user
 */
public class RequestQueue {
    private final PriorityQueue<Request> queue;
    private final Semaphore queueSemaphore;
    private final ScheduledExecutorService agingService;
    private static final int MAX_CONCURRENT_REQUESTS = 5;
    
    public RequestQueue() {
        // Priority queue that considers both priority and wait time
        this.queue = new PriorityQueue<>((r1, r2) -> {
            int priorityCompare = Integer.compare(r2.getPriority(), r1.getPriority());
            if (priorityCompare != 0) return priorityCompare;
            // If priorities are equal, older request gets precedence
            return Long.compare(r1.getCreationTime(), r2.getCreationTime());
        });
        
        // Semaphore to control concurrent access
        this.queueSemaphore = new Semaphore(MAX_CONCURRENT_REQUESTS, true);  // true for fair mode
        
        // Start aging service
        this.agingService = Executors.newSingleThreadScheduledExecutor();
        startAgingProcess();
    }
    
    private void startAgingProcess() {
        agingService.scheduleAtFixedRate(() -> {
            synchronized(queue) {
                queue.forEach(Request::age);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    public void addRequest(Request request) {
        synchronized(queue) {
            queue.offer(request);
        }
        System.out.println("Added request for file: " + request.getFileId() + 
                         ", operation: " + request.getOperationType());
    }
    
    public Request getNextRequest() throws InterruptedException {
        // Try to acquire semaphore
        if (!queueSemaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            System.out.println("Request timed out waiting for semaphore");
            return null;
        }
        
        try {
            synchronized(queue) {
                Request request = queue.poll();
                if (request != null) {
                    System.out.println("Processing request for file: " + request.getFileId() + 
                                     ", waited: " + request.getWaitTime() + "ms, " +
                                     "priority: " + request.getPriority());
                }
                return request;
            }
        } finally {
            queueSemaphore.release();
        }
    }
    
    public void shutdown() {
        agingService.shutdown();
        try {
            if (!agingService.awaitTermination(60, TimeUnit.SECONDS)) {
                agingService.shutdownNow();
            }
        } catch (InterruptedException e) {
            agingService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
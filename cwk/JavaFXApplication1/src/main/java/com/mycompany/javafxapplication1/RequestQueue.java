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
    private static final long AGING_INTERVAL = 5000; // 5 seconds
    
    public RequestQueue() {
        this.queue = new PriorityQueue<>((r1, r2) -> {
            int priorityCompare = Integer.compare(r2.getPriority(), r1.getPriority());
            if (priorityCompare != 0) return priorityCompare;
            return Long.compare(r1.getCreationTime(), r2.getCreationTime());
        });
        
        this.queueSemaphore = new Semaphore(MAX_CONCURRENT_REQUESTS, true);
        this.agingService = Executors.newSingleThreadScheduledExecutor();
        startAgingProcess();
    }
    
    private void startAgingProcess() {
        agingService.scheduleAtFixedRate(() -> {
            synchronized(queue) {
                System.out.println("\n=== Starting Aging Process ===");
                System.out.println("Current queue size: " + queue.size());
                
                List<Request> tempList = new ArrayList<>(queue);
                queue.clear();
                
                for (Request request : tempList) {
                    request.age();
                    queue.offer(request);
                    System.out.println(String.format(
                        "Request %s: waited=%dms, priority=%d",
                        request.getFileId(),
                        request.getWaitTime(),
                        request.getPriority()
                    ));
                }
                
                System.out.println("=== Aging Process Complete ===\n");
            }
        }, 0, AGING_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    public void addRequest(Request request) {
        synchronized(queue) {
            queue.offer(request);
            System.out.println(String.format(
                "Added request: id=%s, type=%s, initial priority=%d",
                request.getFileId(),
                request.getOperationType(),
                request.getPriority()
            ));
        }
    }
    
    public Request getNextRequest() throws InterruptedException {
        if (!queueSemaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            System.out.println("Request timed out waiting for semaphore");
            return null;
        }
        
        try {
            synchronized(queue) {
                Request request = queue.poll();
                if (request != null) {
                    System.out.println(String.format(
                        "Processing request: id=%s, waited=%dms, final priority=%d",
                        request.getFileId(),
                        request.getWaitTime(),
                        request.getPriority()
                    ));
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
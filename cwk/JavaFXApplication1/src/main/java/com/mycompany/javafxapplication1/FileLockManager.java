package com.mycompany.javafxapplication1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages file locks to prevent concurrent access to files during operations.
 * Uses ReentrantLock for thread safety and timeout capabilities.
 */
public class FileLockManager {
    private static final Logger logger = Logger.getLogger(FileLockManager.class.getName());
    private static FileLockManager instance;
    
    // Store active locks for each file using a thread-safe map
    private final ConcurrentHashMap<String, FileLock> fileLocks;
    
    // Maximum time to wait for a lock (in seconds)
    private static final int LOCK_TIMEOUT = 120;
    
    private FileLockManager() {
        fileLocks = new ConcurrentHashMap<>();
    }
    
    public static synchronized FileLockManager getInstance() {
        if (instance == null) {
            instance = new FileLockManager();
        }
        return instance;
    }
    
    /**
     * Represents a file lock with its associated metadata
     */
    private static class FileLock {
        private final ReentrantLock lock;
        private String operationType;
        private String lockedBy;
        private long lockTime;
        
        public FileLock() {
            this.lock = new ReentrantLock(true); 
        }
        
        public void updateLockInfo(String operationType, String user) {
            this.operationType = operationType;
            this.lockedBy = user;
            this.lockTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Attempts to acquire a lock on a file for a specific operation
     * @param fileId Unique identifier of the file
     * @param operationType Type of operation (UPLOAD, DOWNLOAD, DELETE)
     * @param user User attempting the operation
     * @return true if lock was acquired, false if timeout occurred
     */
    public boolean lockFile(String fileId, String operationType, String user) {
        logger.info(String.format("Attempting to lock file %s for %s by user %s", 
                fileId, operationType, user));
        
        FileLock fileLock = fileLocks.computeIfAbsent(fileId, k -> new FileLock());
        
        try {
            // Try to acquire lock with timeout
            if (fileLock.lock.tryLock(LOCK_TIMEOUT, TimeUnit.SECONDS)) {
                fileLock.updateLockInfo(operationType, user);
                logger.info(String.format("Lock acquired for file %s by user %s", fileId, user));
                return true;
            } else {
                logger.warning(String.format("Lock timeout for file %s by user %s", fileId, user));
                return false;
            }
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Lock acquisition interrupted", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Releases a lock on a file
     * @param fileId Unique identifier of the file
     * @param user User releasing the lock
     */
    public void unlockFile(String fileId, String user) {
        FileLock fileLock = fileLocks.get(fileId);
        if (fileLock != null && fileLock.lock.isHeldByCurrentThread()) {
            fileLock.lock.unlock();
            logger.info(String.format("Lock released for file %s by user %s", fileId, user));
            
            // Remove lock if no other threads are waiting
            if (!fileLock.lock.hasQueuedThreads()) {
                fileLocks.remove(fileId);
            }
        }
    }
    
    /**
     * Checks if a file is currently locked
     * @param fileId Unique identifier of the file
     * @return true if file is locked, false otherwise
     */
    public boolean isFileLocked(String fileId) {
        FileLock fileLock = fileLocks.get(fileId);
        return fileLock != null && fileLock.lock.isLocked();
    }
    
    /**
     * Gets information about a file's lock status
     * @param fileId Unique identifier of the file
     * @return String describing the lock status
     */
    public String getLockInfo(String fileId) {
        FileLock fileLock = fileLocks.get(fileId);
        if (fileLock != null && fileLock.lock.isLocked()) {
            long lockDuration = (System.currentTimeMillis() - fileLock.lockTime) / 1000;
            return String.format("Locked by %s for %s operation (Duration: %d seconds)",
                    fileLock.lockedBy, fileLock.operationType, lockDuration);
        }
        return "Not locked";
    }
}
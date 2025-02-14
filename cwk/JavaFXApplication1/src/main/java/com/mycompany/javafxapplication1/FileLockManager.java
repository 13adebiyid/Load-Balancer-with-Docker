package com.mycompany.javafxapplication1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages file locks 
 */
public class FileLockManager {
    private static final Logger logger = Logger.getLogger(FileLockManager.class.getName());
    private static FileLockManager instance;
    
    private final ConcurrentHashMap<String, FileLock> fileLocks;
    
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
     * Represents file lock 
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
     * Acquire lock on a file
     */
    public boolean lockFile(String fileId, String operationType, String user) {
        logger.info(String.format("Attempting to lock file %s for %s by user %s", fileId, operationType, user));
        
        FileLock fileLock = fileLocks.computeIfAbsent(fileId, k -> new FileLock());
        
        try {
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
     * Releases lock on file
     */
    public void unlockFile(String fileId, String user) {
        FileLock fileLock = fileLocks.get(fileId);
        if (fileLock != null && fileLock.lock.isHeldByCurrentThread()) {
            fileLock.lock.unlock();
            logger.info(String.format("Lock released for file %s by user %s", fileId, user));
            
            if (!fileLock.lock.hasQueuedThreads()) {
                fileLocks.remove(fileId);
            }
        }
    }
    
    /**
     * Checks if file is locked
     */
    public boolean isFileLocked(String fileId) {
        FileLock fileLock = fileLocks.get(fileId);
        return fileLock != null && fileLock.lock.isLocked();
    }
    
    /**
     * Gets information about file's lock status
     */
    public String getLockInfo(String fileId) {
        FileLock fileLock = fileLocks.get(fileId);
        if (fileLock != null && fileLock.lock.isLocked()) {
            long lockDuration = (System.currentTimeMillis() - fileLock.lockTime) / 1000;
            return String.format("Locked by %s for %s operation (Duration: %d seconds)",fileLock.lockedBy, fileLock.operationType, lockDuration);
        }
        return "Not locked";
    }
}
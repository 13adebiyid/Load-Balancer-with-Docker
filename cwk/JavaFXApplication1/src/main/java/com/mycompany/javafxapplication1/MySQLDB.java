package com.mycompany.javafxapplication1;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javafx.collections.ObservableList;

/**
 * Handles MySQL database operations and synchronization with SQLite
 */
public class MySQLDB {
    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/comp20081";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "root";
    private Connection mysqlConnection;
    private final DB sqliteDB;
    private final ScheduledExecutorService syncExecutor;
    private final ScheduledExecutorService cleanupExecutor;
    private static final Logger logger = Logger.getLogger(MySQLDB.class.getName());
    
    public MySQLDB(DB sqliteDB) {
        this.sqliteDB = sqliteDB;
        this.syncExecutor = Executors.newSingleThreadScheduledExecutor();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        initializeDatabase();
        startSyncScheduler();
        startCleanupScheduler();
    }
    
    /**
     * Initializes MySQL database and creates required tables
     */
    private void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            mysqlConnection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
            
            // Create required tables
            try (Statement stmt = mysqlConnection.createStatement()) {
                // Users table
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255) NOT NULL UNIQUE, password VARCHAR(255) NOT NULL, is_admin BOOLEAN DEFAULT FALSE, last_sync TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                
                // Files table
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS files (file_id VARCHAR(255) PRIMARY KEY, file_name VARCHAR(255) NOT NULL, owner_user VARCHAR(255) NOT NULL, total_size BIGINT NOT NULL, total_chunks INT NOT NULL, is_shared BOOLEAN DEFAULT FALSE, last_sync TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                
                // File chunks table
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS file_chunks (file_id VARCHAR(255), chunk_number INT, container_id VARCHAR(255) NOT NULL, PRIMARY KEY (file_id, chunk_number), FOREIGN KEY (file_id) REFERENCES files(file_id) ON DELETE CASCADE)");
                
                // File permissions table
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS file_permissions (file_id VARCHAR(255), user_name VARCHAR(255), can_read BOOLEAN DEFAULT FALSE, can_write BOOLEAN DEFAULT FALSE, PRIMARY KEY (file_id, user_name), FOREIGN KEY (file_id) REFERENCES files(file_id) ON DELETE CASCADE)");
                
                logger.info("MySQL tables initialized successfully");
            }
            
        } catch (SQLException | ClassNotFoundException e) {
            logger.severe("Failed to initialize MySQL database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Starts periodic synchronization between MySQL and SQLite
     */
    private void startSyncScheduler() {
        syncExecutor.scheduleAtFixedRate(this::synchronizeDatabases,
                0, 5, TimeUnit.MINUTES);
    }
    
    private void startCleanupScheduler() {
        // Run cleanup every hour
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                sqliteDB.cleanupExpiredSessions();
            } catch (Exception e) {
                logger.severe("Failed to cleanup temporary data: " + e.getMessage());
            }
        }, 1, 60, TimeUnit.MINUTES);
    }
    
    /**
     * Synchronizes data between MySQL and SQLite databases
     */
    private void synchronizeDatabases() {
        try {
            mysqlConnection.setAutoCommit(false);
            
            // Sync users
            syncUsers();
            
            // Sync files and chunks
            syncFiles();
            
            // Sync file permissions
            syncFilePermissions();
            
            mysqlConnection.commit();
            logger.info("Database synchronization completed successfully");
            
        } catch (Exception e) {
            try {
                mysqlConnection.rollback();
            } catch (SQLException ex) {
                logger.severe("Failed to rollback transaction: " + ex.getMessage());
            }
            logger.severe("Database synchronization failed: " + e.getMessage());
        } finally {
            try {
                mysqlConnection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.severe("Failed to reset auto-commit: " + e.getMessage());
            }
        }
    }
    
    /**
     * Synchronizes user data between databases
     */
    private void syncUsers() throws SQLException, ClassNotFoundException {
        // Get users from SQLite
        ObservableList<User> sqliteUsers = sqliteDB.getDataFromTable();
        
        // Prepare MySQL statement
        String upsertUser = "INSERT INTO users (name, password, role) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE password = VALUES(password), role = VALUES(role)";
        
        try (PreparedStatement pstmt = mysqlConnection.prepareStatement(upsertUser)) {
            for (User user : sqliteUsers) {
                pstmt.setString(1, user.getUser());
                pstmt.setString(2, user.getPass());
                pstmt.setString(3, user.getRole());
                pstmt.executeUpdate();
            }
        }
    }
    
    /**
     * Synchronizes file metadata between databases
     */
    private void syncFiles() throws SQLException, ClassNotFoundException {
        // Get files from SQLite
        List<FileMetadata> sqliteFiles = sqliteDB.getAllFiles();
        
        // Prepare MySQL statements
        String insertFile = "INSERT INTO files (file_id, file_name, owner_user, total_size, total_chunks, is_shared) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE file_name = ?, owner_user = ?, total_size = ?, total_chunks = ?, is_shared = ?";
        String insertChunk = "INSERT INTO file_chunks (file_id, chunk_number, container_id) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE container_id = ?";
        
        try (PreparedStatement fileStmt = mysqlConnection.prepareStatement(insertFile);
                PreparedStatement chunkStmt = mysqlConnection.prepareStatement(insertChunk)) {
            
            for (FileMetadata file : sqliteFiles) {
                // Insert/update file metadata
                fileStmt.setString(1, file.getFileId());
                fileStmt.setString(2, file.getFileName());
                fileStmt.setString(3, file.getOwnerUser());
                fileStmt.setLong(4, file.getTotalSize());
                fileStmt.setInt(5, file.getTotalChunks());
                fileStmt.setBoolean(6, file.isShared());
                // Set update values
                fileStmt.setString(7, file.getFileName());
                fileStmt.setString(8, file.getOwnerUser());
                fileStmt.setLong(9, file.getTotalSize());
                fileStmt.setInt(10, file.getTotalChunks());
                fileStmt.setBoolean(11, file.isShared());
                fileStmt.executeUpdate();
                
                // Insert/update chunk locations
                for (int i = 0; i < file.getTotalChunks(); i++) {
                    String containerId = file.getContainerForChunk(i);
                    if (containerId != null) {
                        chunkStmt.setString(1, file.getFileId());
                        chunkStmt.setInt(2, i);
                        chunkStmt.setString(3, containerId);
                        chunkStmt.setString(4, containerId);
                        chunkStmt.executeUpdate();
                    }
                }
            }
        }
    }
    
    /**
     * Synchronizes file permissions between databases
     */
    private void syncFilePermissions() throws SQLException {
        // Implement file permissions sync when ACL feature is added
        // This is a placeholder for now
    }
    
    /**
     * Closes database connections and stops sync scheduler
     */
    public void shutdown() {
        syncExecutor.shutdown();
        try {
            if (mysqlConnection != null && !mysqlConnection.isClosed()) {
                mysqlConnection.close();
            }
        } catch (SQLException e) {
            logger.severe("Error closing MySQL connection: " + e.getMessage());
        }
    }
}
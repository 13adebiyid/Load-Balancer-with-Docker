package com.mycompany.javafxapplication1;

import java.sql.*;
import java.util.*;
import java.util.logging.*;

/**
 * Handles file permissions and Access Control Lists (ACLs) for the distributed storage system.
 * Provides functionality for setting, checking, and managing file access permissions per user.
 */
public class FilePermissions {
    private final DB sqliteDB;
    private static final Logger logger = Logger.getLogger(FilePermissions.class.getName());
    
    public FilePermissions(DB sqliteDB) {
        this.sqliteDB = sqliteDB;
        initializePermissionsTable();
    }
    
    /**
     * Creates the permissions table if it doesn't exist.
     * This table stores the ACL entries for each file-user combination.
     */
    private void initializePermissionsTable() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:comp20081.db")) {
                Statement stmt = conn.createStatement();
                
                // Create permissions table with foreign key constraints
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS file_permissions (" +
                    "file_id TEXT, " +
                    "user_name TEXT, " +
                    "can_read INTEGER DEFAULT 0, " +
                    "can_write INTEGER DEFAULT 0, " +
                    "date_granted TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "granted_by TEXT, " +
                    "PRIMARY KEY (file_id, user_name), " +
                    "FOREIGN KEY (file_id) REFERENCES files(file_id) ON DELETE CASCADE)"
                );
                
                logger.info("Permissions table initialized successfully");
            }
        } catch (Exception e) {
            logger.severe("Failed to initialize permissions table: " + e.getMessage());
            throw new RuntimeException("Failed to initialize permissions", e);
        }
    }
    
    /**
     * Sets or updates permissions for a user on a specific file
     * @param fileId The unique identifier of the file
     * @param userName The username to grant permissions to
     * @param canRead Whether the user can read the file
     * @param canWrite Whether the user can modify the file
     * @param grantedBy The username of the user granting these permissions
     */
    public void setPermissions(String fileId, String userName, boolean canRead, 
                             boolean canWrite, String grantedBy) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:comp20081.db")) {
            String sql = "INSERT OR REPLACE INTO file_permissions " +
                        "(file_id, user_name, can_read, can_write, granted_by) " +
                        "VALUES (?, ?, ?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, fileId);
                pstmt.setString(2, userName);
                pstmt.setInt(3, canRead ? 1 : 0);
                pstmt.setInt(4, canWrite ? 1 : 0);
                pstmt.setString(5, grantedBy);
                pstmt.executeUpdate();
                
                logger.info(String.format("Set permissions for user %s on file %s: read=%b, write=%b",
                    userName, fileId, canRead, canWrite));
            }
        } catch (SQLException e) {
            logger.severe("Failed to set permissions: " + e.getMessage());
            throw new RuntimeException("Failed to set permissions", e);
        }
    }
    
    /**
     * Checks if a user has specific permissions on a file
     * @param fileId The file to check permissions for
     * @param userName The user to check permissions for
     * @param permission The permission to check ("read" or "write")
     * @return true if the user has the specified permission
     */
    public boolean checkPermission(String fileId, String userName, String permission) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:comp20081.db")) {
            // First check if user is the owner (owners have full permissions)
            String ownerSql = "SELECT owner_user FROM files WHERE file_id = ?";
            try (PreparedStatement ownerStmt = conn.prepareStatement(ownerSql)) {
                ownerStmt.setString(1, fileId);
                ResultSet ownerRs = ownerStmt.executeQuery();
                if (ownerRs.next() && userName.equals(ownerRs.getString("owner_user"))) {
                    return true;
                }
            }
            
            // Check explicit permissions
            String sql = "SELECT can_read, can_write FROM file_permissions " +
                        "WHERE file_id = ? AND user_name = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, fileId);
                pstmt.setString(2, userName);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    switch (permission.toLowerCase()) {
                        case "read":
                            return rs.getInt("can_read") == 1;
                        case "write":
                            return rs.getInt("can_write") == 1;
                        default:
                            return false;
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to check permissions: " + e.getMessage());
            throw new RuntimeException("Failed to check permissions", e);
        }
        return false;
    }
    
    /**
     * Gets a list of all users who have access to a file and their permissions
     * @param fileId The file to get permissions for
     * @return List of maps containing user access details
     */
    public List<Map<String, Object>> getFileAccessList(String fileId) {
        List<Map<String, Object>> accessList = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:comp20081.db")) {
            String sql = "SELECT user_name, can_read, can_write, date_granted, granted_by " +
                        "FROM file_permissions WHERE file_id = ? " +
                        "ORDER BY date_granted DESC";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, fileId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> userAccess = new HashMap<>();
                    userAccess.put("userName", rs.getString("user_name"));
                    userAccess.put("canRead", rs.getInt("can_read") == 1);
                    userAccess.put("canWrite", rs.getInt("can_write") == 1);
                    userAccess.put("dateGranted", rs.getTimestamp("date_granted"));
                    userAccess.put("grantedBy", rs.getString("granted_by"));
                    accessList.add(userAccess);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get access list: " + e.getMessage());
            throw new RuntimeException("Failed to get access list", e);
        }
        
        return accessList;
    }
    
    /**
     * Removes all permissions for a file (used when deleting a file)
     * @param fileId The file to remove all permissions for
     */
    public void removeAllPermissions(String fileId) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:comp20081.db")) {
            String sql = "DELETE FROM file_permissions WHERE file_id = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, fileId);
                int count = pstmt.executeUpdate();
                
                logger.info(String.format("Removed %d permission entries for file %s", 
                    count, fileId));
            }
        } catch (SQLException e) {
            logger.severe("Failed to remove permissions: " + e.getMessage());
            throw new RuntimeException("Failed to remove permissions", e);
        }
    }
    
    /**
     * Revokes specific permissions for a user on a file
     * @param fileId The file to modify permissions for
     * @param userName The user to revoke permissions from
     * @param revokeRead Whether to revoke read permission
     * @param revokeWrite Whether to revoke write permission
     */
    public void revokePermissions(String fileId, String userName, 
                                boolean revokeRead, boolean revokeWrite) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:comp20081.db")) {
            // First get current permissions
            String getSql = "SELECT can_read, can_write FROM file_permissions " +
                          "WHERE file_id = ? AND user_name = ?";
            
            boolean currentRead = false;
            boolean currentWrite = false;
            
            try (PreparedStatement getStmt = conn.prepareStatement(getSql)) {
                getStmt.setString(1, fileId);
                getStmt.setString(2, userName);
                ResultSet rs = getStmt.executeQuery();
                
                if (rs.next()) {
                    currentRead = rs.getInt("can_read") == 1;
                    currentWrite = rs.getInt("can_write") == 1;
                }
            }
            
            // Calculate new permissions
            boolean newRead = currentRead && !revokeRead;
            boolean newWrite = currentWrite && !revokeWrite;
            
            // If both permissions are being revoked, delete the entry
            if (!newRead && !newWrite) {
                String deleteSql = "DELETE FROM file_permissions " +
                                 "WHERE file_id = ? AND user_name = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, fileId);
                    deleteStmt.setString(2, userName);
                    deleteStmt.executeUpdate();
                }
            } else {
                // Otherwise update the permissions
                String updateSql = "UPDATE file_permissions SET can_read = ?, can_write = ? " +
                                 "WHERE file_id = ? AND user_name = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setInt(1, newRead ? 1 : 0);
                    updateStmt.setInt(2, newWrite ? 1 : 0);
                    updateStmt.setString(3, fileId);
                    updateStmt.setString(4, userName);
                    updateStmt.executeUpdate();
                }
            }
            
            logger.info(String.format("Revoked permissions for user %s on file %s: read=%b, write=%b",
                userName, fileId, revokeRead, revokeWrite));
                
        } catch (SQLException e) {
            logger.severe("Failed to revoke permissions: " + e.getMessage());
            throw new RuntimeException("Failed to revoke permissions", e);
        }
    }
}
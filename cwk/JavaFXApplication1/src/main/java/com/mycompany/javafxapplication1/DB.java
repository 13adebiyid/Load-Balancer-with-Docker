/*
* Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
* Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
*/
package com.mycompany.javafxapplication1;

import java.io.*;
import java.sql.*;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 *
 * @author ntu-user
 */
public class DB {
    private String fileName = "jdbc:sqlite:comp20081.db";
    private int timeout = 30;
    private String dataBaseName = "COMP20081";
    private String dataBaseTableName = "Users";
    Connection connection = null;
    private Random random = new SecureRandom();
    private String characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private int iterations = 10000;
    private int keylength = 256;
    private String saltValue;
    private static final String MYSQL_HOST = "comp20081-mysql-db";
    private static final String MYSQL_PORT = "3306";
    private static final String MYSQL_DATABASE = "comp20081";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "root";
    
    
    
    /**
     * Constructor - initializes the database and creates required tables
     * Also handles salt generation/loading for password encryption
     */
    DB() {
        try {
            // Handle salt initialization (your existing code)
            File fp = new File(".salt");
            if (!fp.exists()) {
                saltValue = this.getSaltvalue(30);
                FileWriter myWriter = new FileWriter(fp);
                myWriter.write(saltValue);
                myWriter.close();
            } else {
                Scanner myReader = new Scanner(fp);
                while (myReader.hasNextLine()) {
                    saltValue = myReader.nextLine();
                }
            }
            
            // Initialize database tables
            createTables();
            
        } catch (IOException e) {
            System.err.println("Error handling salt file: " + e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("Error creating database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates all required database tables for the distributed file storage system.
     * This includes tables for:
     * - Users: Storing user authentication information
     * - Files: Tracking metadata about stored files
     * - File_chunks: Managing the distribution of file chunks across containers
     * - Encryption_keys: Storing encryption keys for secure file storage
     */
    public void createTables() throws ClassNotFoundException {
        try {
            // Establish database connection
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            connection.setAutoCommit(false);
            
            // Create users table with role column
            statement.executeUpdate("create table if not exists " + dataBaseTableName +
                    "(id integer primary key autoincrement, name string, password string, role string default 'STANDARD')");
            
            // Check for admin user within the same connection
            ResultSet rs = statement.executeQuery("SELECT COUNT(*) as count FROM " + dataBaseTableName +
                    " WHERE role = 'ADMIN'");
            
            if (rs.next() && rs.getInt("count") == 0) {
                // Add admin user using the same connection
                String adminPass = null;
                try {
                    adminPass = generateSecurePassword("admin");
                } catch (InvalidKeySpecException ex) {
                    Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
                }
                statement.executeUpdate("INSERT INTO " + dataBaseTableName +
                        " (name, password, role) VALUES('admin', '" + adminPass + "', 'ADMIN')");
                System.out.println("Added default admin user");
            }
            
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS sessions (" +
                    "session_id TEXT PRIMARY KEY," +
                    "user_name TEXT NOT NULL," +
                    "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_access TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "expires_at TIMESTAMP," +
                    "FOREIGN KEY (user_name) REFERENCES " + dataBaseTableName + "(name)" +
                ")"
            );
            
            statement.executeUpdate(
                    "create table if not exists file_permissions (" +
                            "file_id text," +                       // Links to files table
                            "user_name text," +                     // User who has permission
                            "can_read integer default 0," +         // Read permission
                            "can_write integer default 0," +        // Write permission
                            "date_granted timestamp default current_timestamp," + // When permission was granted
                            "granted_by text," +                    // Who granted the permission
                            "primary key (file_id, user_name)," +
                            "foreign key (file_id) references files(file_id) on delete cascade" +
                            ")"
            );
            
            
            // Create files table to track overall file information
            statement.executeUpdate(
                    "create table if not exists files (" +
                            "file_id text primary key," +           // Unique identifier for each file
                            "file_name text not null," +            // Original name of the file
                            "owner_user text not null," +           // User who owns this file
                            "total_size integer not null," +        // Total size in bytes
                            "total_chunks integer not null," +      // Number of chunks file is split into
                            "is_shared integer default 0" +         // Whether file is shared
                            ")"
            );
            
            // Create file_chunks table to track chunk distribution
            statement.executeUpdate(
                    "create table if not exists file_chunks (" +
                            "file_id text," +                       // Links to files table
                            "chunk_number integer," +               // Sequence number of chunk
                            "container_id text not null," +         // Which container stores this chunk
                            "primary key (file_id, chunk_number)," +
                            "foreign key (file_id) references files(file_id)" +
                            ")"
            );
            
            // Create encryption_keys table for secure storage
            statement.executeUpdate(
                    "create table if not exists encryption_keys (" +
                            "file_id text," +                       // Links to files table
                            "chunk_number integer," +               // Which chunk this key is for
                            "key_string text not null," +           //encryption key
                            "primary key (file_id, chunk_number)," +
                            "foreign key (file_id) references files(file_id)" +
                            ")"
            );
            connection.commit();
            System.out.println("Database tables initialized successfully");
            
        } catch (SQLException ex) {
            // Rollback on error
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e) {
                System.err.println("Error rolling back transaction: " + e.getMessage());
            }
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE,
                    "Failed to create database tables", ex);
            throw new RuntimeException("Database initialization failed", ex);
        } finally {
            try {
                
                if (connection != null) {
                    connection.setAutoCommit(true);  // Reset auto-commit
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing database resources: " + e.getMessage());
            }
        }
    }
    
    /**
     * Initializes the database for first use or validates existing structure.
     * This method ensures database persistence while maintaining data integrity.
     * It creates tables only if they don't exist and validates the database structure.
     */
    public void initializeDatabase() throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            ResultSet tables = connection.getMetaData().getTables(null, null, "files", null);
            boolean needsInitialization = !tables.next();
            
            if (needsInitialization) {
                System.out.println("First-time database initialization - creating tables");
                createTables();
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE,
                    "Database initialization failed", ex);
            throw new RuntimeException("Database initialization failed", ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Deletes a file's metadata and associated records from the database
     * This includes removing entries from:
     * - files table
     * - file_chunks table
     * - encryption_keys table
     * @param fileId The unique identifier of the file to delete
     * @throws ClassNotFoundException if the database driver cannot be loaded
     */
    public void deleteFileMetadata(String fileId) throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            
            connection.setAutoCommit(false);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            try {
                statement.executeUpdate(
                        "DELETE FROM encryption_keys WHERE file_id = '" + fileId + "'"
                );
                
                statement.executeUpdate(
                        "DELETE FROM file_chunks WHERE file_id = '" + fileId + "'"
                );
                
                statement.executeUpdate(
                        "DELETE FROM files WHERE file_id = '" + fileId + "'"
                );
                
                connection.commit();
                System.out.println("Successfully deleted metadata for file: " + fileId);
                
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE,
                    "Failed to delete file metadata: " + ex.getMessage(), ex);
            throw new RuntimeException("Failed to delete file metadata", ex);
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);  // Reset auto-commit mode
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Retrieves all files from the database
     * @return List of FileMetadata objects
     */
    public List<FileMetadata> getAllFiles() throws ClassNotFoundException {
        List<FileMetadata> files = new ArrayList<>();
        Connection conn = null;
        Statement stmt = null;
        
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(fileName);
            stmt = conn.createStatement();
            stmt.setQueryTimeout(timeout);
            
            ResultSet rs = stmt.executeQuery("SELECT * FROM files");
            
            while (rs.next()) {
                FileMetadata metadata = new FileMetadata(
                        rs.getString("file_id"),
                        rs.getString("file_name"),
                        rs.getString("owner_user"),
                        rs.getLong("total_size")
                );
                metadata.setShared(rs.getInt("is_shared") == 1);
                
                try (Statement chunkStmt = conn.createStatement()) {
                    ResultSet chunksRs = chunkStmt.executeQuery(
                            "SELECT chunk_number, container_id FROM file_chunks " +
                                    "WHERE file_id = '" + metadata.getFileId() + "' " +
                                            "ORDER BY chunk_number"
                    );
                    
                    while (chunksRs.next()) {
                        metadata.addChunkLocation(
                                chunksRs.getInt("chunk_number"),
                                chunksRs.getString("container_id")
                        );
                    }
                }
                
                files.add(metadata);
            }
            
            return files;
            
        } catch (SQLException e) {
            System.err.println("Error retrieving files: " + e.getMessage());
            throw new RuntimeException("Database error", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing database resources: " + e.getMessage());
            }
        }
    }
    
    /**
     * @brief delete table
     * @param tableName of type String
     */
    public void delTable(String tableName) throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            statement.executeUpdate("drop table if exists " + tableName);
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
    }
    
    /**
     * @brief add data to the database method
     * @param user name of type String
     * @param password of type String
     */
    public void addDataToDB(String user, String password, String role)
            throws InvalidKeySpecException, ClassNotFoundException {
        Connection conn = null;
        Statement stmt = null;
        try {
            // Use a single connection
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(fileName);
            conn.setAutoCommit(false);  // Start transaction
            
            stmt = conn.createStatement();
            stmt.setQueryTimeout(timeout);
            
            String securePass = generateSecurePassword(password);
            System.out.println("Adding User: " + user + ", Role: " + role);
            
            stmt.executeUpdate("INSERT INTO " + dataBaseTableName +
                    " (name, password, role) VALUES('" + user + "','" +
                    securePass + "','" + role + "')");
            
            conn.commit();  // Commit transaction
            
        } catch (SQLException ex) {
            try {
                if (conn != null) {
                    conn.rollback();  // Rollback on error
                }
            } catch (SQLException e) {
                System.err.println("Error rolling back transaction: " + e.getMessage());
            }
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) {
                    conn.setAutoCommit(true);  // Reset auto-commit
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing database resources: " + e.getMessage());
            }
        }
    }
    
    /**
     * @brief get data from the Database method
     * @retunr results as ResultSet
     */
    public ObservableList<User> getDataFromTable() throws ClassNotFoundException {
        ObservableList<User> result = FXCollections.observableArrayList();
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            ResultSet rs = statement.executeQuery("select * from " + this.dataBaseTableName);
            while (rs.next()) {
                result.add(new User(rs.getString("name"),rs.getString("password")));
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        return result;
    }
    
    /**
     * @brief decode password method
     * @param user name as type String
     * @param pass plain password of type String
     * @return true if the credentials are valid, otherwise false
     */
    public boolean validateUser(String user, String pass) throws InvalidKeySpecException, ClassNotFoundException {
        Boolean flag = false;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            ResultSet rs = statement.executeQuery("select name, password from " + this.dataBaseTableName);
            String inPass = generateSecurePassword(pass);
            
            while (rs.next()) {
                if (user.equals(rs.getString("name")) && rs.getString("password").equals(inPass)) {
                    flag = true;
                    break;
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        
        return flag;
    }
    
    private String getSaltvalue(int length) {
        StringBuilder finalval = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            finalval.append(characters.charAt(random.nextInt(characters.length())));
        }
        
        return new String(finalval);
    }
    
    /* Method that generates hash value */
    private byte[] hash(char[] password, byte[] salt) throws InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keylength);
        Arrays.fill(password, Character.MIN_VALUE);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new AssertionError("Error while hashing a password: " + e.getMessage(), e);
        } finally {
            spec.clearPassword();
        }
    }
    
    public String generateSecurePassword(String password) throws InvalidKeySpecException {
        String finalval = null;
        
        byte[] securePassword = hash(password.toCharArray(), saltValue.getBytes());
        
        finalval = Base64.getEncoder().encodeToString(securePassword);
        
        return finalval;
    }
    
    /**
     * @brief get table name
     * @return table name as String
     */
    public String getTableName() {
        return this.dataBaseTableName;
    }
    
    /**
     * @brief print a message on screen method
     * @param message of type String
     */
    public void log(String message) {
        System.out.println(message);
        
    }
    
    public void saveFileMetadata(FileMetadata metadata) throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            connection.setAutoCommit(false);  // Start transaction
            
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            try {
                // Escape special characters in the file name and other strings
                String escapedFileName = metadata.getFileName().replace("'", "''");
                String escapedFileId = metadata.getFileId().replace("'", "''");
                String escapedOwnerUser = metadata.getOwnerUser().replace("'", "''");
                
                // Add file metadata using prepared statement to prevent SQL injection
                String insertFileSql = "INSERT INTO files " +
                        "(file_id, file_name, owner_user, total_size, total_chunks, is_shared) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";
                
                try (PreparedStatement pstmt = connection.prepareStatement(insertFileSql)) {
                    pstmt.setString(1, metadata.getFileId());
                    pstmt.setString(2, metadata.getFileName());
                    pstmt.setString(3, metadata.getOwnerUser());
                    pstmt.setLong(4, metadata.getTotalSize());
                    pstmt.setInt(5, metadata.getTotalChunks());
                    pstmt.setInt(6, metadata.isShared() ? 1 : 0);
                    pstmt.executeUpdate();
                }
                
                // Add chunk locations using prepared statement
                String insertChunkSql = "INSERT INTO file_chunks (file_id, chunk_number, container_id) VALUES (?, ?, ?)";
                try (PreparedStatement chunkStmt = connection.prepareStatement(insertChunkSql)) {
                    for (int i = 0; i < metadata.getTotalChunks(); i++) {
                        String containerId = metadata.getContainerForChunk(i);
                        if (containerId != null) {
                            chunkStmt.setString(1, metadata.getFileId());
                            chunkStmt.setInt(2, i);
                            chunkStmt.setString(3, containerId);
                            chunkStmt.executeUpdate();
                        }
                    }
                }
                
                connection.commit();
                System.out.println("Successfully saved metadata for file: " + metadata.getFileName());
                
            } catch (SQLException e) {
                // If fail, rolls back the entire transaction
                if (connection != null) {
                    connection.rollback();
                }
                throw e;
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE,
                    "Failed to save file metadata: " + ex.getMessage(), ex);
            throw new RuntimeException("Failed to save file metadata", ex);
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);  // Reset auto-commit
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
    
    public FileMetadata getFileMetadata(String fileId) throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            ResultSet rs = statement.executeQuery(
                    "SELECT * FROM files WHERE file_id = '" + fileId + "'"
            );
            
            if (rs.next()) {
                FileMetadata metadata = new FileMetadata(
                        rs.getString("file_id"),
                        rs.getString("file_name"),
                        rs.getString("owner_user"),
                        rs.getLong("total_size")
                );
                
                metadata.setShared(rs.getInt("is_shared") == 1);
                
                ResultSet chunksRs = statement.executeQuery(
                        "SELECT chunk_number, container_id FROM file_chunks " +
                                "WHERE file_id = '" + fileId + "' ORDER BY chunk_number"
                );
                
                while (chunksRs.next()) {
                    metadata.addChunkLocation(
                            chunksRs.getInt("chunk_number"),
                            chunksRs.getString("container_id")
                    );
                }
                
                return metadata;
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Sets or updates permissions for a user on a file
     */
    public void setFilePermissions(String fileId, String userName, boolean canRead,
            boolean canWrite, String grantedBy) throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            statement.executeUpdate(
                    "INSERT OR REPLACE INTO file_permissions (file_id, user_name, can_read, can_write, granted_by) " +
                            "VALUES ('" + fileId + "', '" + userName + "', " +
                            (canRead ? 1 : 0) + ", " + (canWrite ? 1 : 0) + ", '" + grantedBy + "')"
            );
            
            System.out.println("Updated permissions for user " + userName + " on file " + fileId);
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Failed to set file permissions", ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
    }
    
    /**
     * Checks if a user has specific permission on a file
     */
    public boolean checkFilePermission(String fileId, String userName, String permission)
            throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            //check if user is the owner
            ResultSet ownerRs = statement.executeQuery(
                    "SELECT owner_user FROM files WHERE file_id = '" + fileId + "'"
            );
            
            if (ownerRs.next() && userName.equals(ownerRs.getString("owner_user"))) {
                return true;
            }
            
            // Check other permissions
            ResultSet rs = statement.executeQuery(
                    "SELECT can_read, can_write FROM file_permissions " +
                            "WHERE file_id = '" + fileId + "' AND user_name = '" + userName + "'"
            );
            
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
            
            return false;
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
    }
    
    /**
     * Gets list of users who have access to a file
     */
    public List<Map<String, Object>> getFileAccessList(String fileId) throws ClassNotFoundException {
        List<Map<String, Object>> accessList = new ArrayList<>();
        
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            ResultSet rs = statement.executeQuery(
                    "SELECT user_name, can_read, can_write, date_granted, granted_by " +
                            "FROM file_permissions WHERE file_id = '" + fileId + "' " +
                                    "ORDER BY date_granted DESC"
            );
            
            while (rs.next()) {
                Map<String, Object> userAccess = new HashMap<>();
                userAccess.put("userName", rs.getString("user_name"));
                userAccess.put("canRead", rs.getInt("can_read") == 1);
                userAccess.put("canWrite", rs.getInt("can_write") == 1);
                userAccess.put("dateGranted", rs.getTimestamp("date_granted"));
                userAccess.put("grantedBy", rs.getString("granted_by"));
                accessList.add(userAccess);
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        
        return accessList;
    }
    
    public void storeEncryptionKey(String fileId, int chunkNumber, String keyString)
            throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS encryption_keys (" +
                            "file_id TEXT, chunk_number INTEGER, key_string TEXT, " +
                            "PRIMARY KEY (file_id, chunk_number))"
            );
            
            statement.executeUpdate(
                    "INSERT OR REPLACE INTO encryption_keys (file_id, chunk_number, key_string) " +
                            "VALUES ('" + fileId + "', " + chunkNumber + ", '" + keyString + "')"
            );
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
    }
    
    public String getEncryptionKey(String fileId, int chunkNumber) throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            ResultSet rs = statement.executeQuery(
                    "SELECT key_string FROM encryption_keys " +
                            "WHERE file_id = '" + fileId + "' AND chunk_number = " + chunkNumber
            );
            
            if (rs.next()) {
                return rs.getString("key_string");
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        
        return null;
    }
    
    private String getMySQLUrl() {
        return String.format("jdbc:mysql://%s:%s/%s",
                MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE);
    }
    
    // Add method to test MySQL connection
    public boolean testMySQLConnection() {
        try {
            Connection conn = DriverManager.getConnection(
                    getMySQLUrl(), MYSQL_USER, MYSQL_PASSWORD);
            conn.close();
            return true;
        } catch (SQLException e) {
            System.err.println("MySQL connection failed: " + e.getMessage());
            return false;
        }
    }
    
    public String getUserRole(String username) throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            ResultSet rs = statement.executeQuery("SELECT role FROM " + dataBaseTableName +
                    " WHERE name = '" + username + "'");
            if (rs.next()) {
                return rs.getString("role");
            }
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        return "STANDARD";
    }
    
    public void createUserSession(String userName) throws SQLException, ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            String sessionId = UUID.randomUUID().toString();
            statement.executeUpdate(
                "INSERT INTO sessions (session_id, user_name, expires_at) " +
                "VALUES ('" + sessionId + "', '" + userName + "', " +
                "datetime('now', '+24 hours'))"
            );
            
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    public void cleanupExpiredSessions() throws SQLException, ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            statement.executeUpdate("DELETE FROM sessions WHERE expires_at < datetime('now')");
            
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
    
}

/*
* Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
* Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
*/
package com.mycompany.javafxapplication1;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
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
            
            // Create users table (maintaining existing functionality)
            statement.executeUpdate("create table if not exists " + dataBaseTableName +
                    "(id integer primary key autoincrement, name string, password string)");
            
            // Create files table to track overall file information
            // This stores metadata about each file in the system
            statement.executeUpdate(
                    "create table if not exists files (" +
                            "file_id text primary key," +           // Unique identifier for each file
                            "file_name text not null," +            // Original name of the file
                            "owner_user text not null," +           // User who owns this file
                            "total_size integer not null," +        // Total size in bytes
                            "total_chunks integer not null," +      // Number of chunks file is split into
                            "is_shared integer default 0" +         // Whether file is shared (0=false, 1=true)
                            ")"
            );
            
            // Create file_chunks table to track chunk distribution
            // This maps each chunk to its container location
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
            // This stores encryption keys for each file chunk
            statement.executeUpdate(
                    "create table if not exists encryption_keys (" +
                            "file_id text," +                       // Links to files table
                            "chunk_number integer," +               // Which chunk this key is for
                            "key_string text not null," +           // The encryption key itself
                            "primary key (file_id, chunk_number)," +
                            "foreign key (file_id) references files(file_id)" +
                            ")"
            );
            
            System.out.println("Database tables initialized successfully");
            
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE,
                    "Failed to create database tables", ex);
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
            
            // Check if this is a first-time initialization
            ResultSet tables = connection.getMetaData().getTables(null, null, "files", null);
            boolean needsInitialization = !tables.next();
            
            if (needsInitialization) {
                System.out.println("First-time database initialization - creating tables");
                createTables();
                
                // Add default admin user if this is first initialization
                try {
                    addDataToDB("admin", "admin123");
                    System.out.println("Created default admin account");
                } catch (InvalidKeySpecException e) {
                    System.err.println("Failed to create default admin account: " + e.getMessage());
                }
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
        
        // Use a transaction to ensure all related records are deleted atomically
        connection.setAutoCommit(false);
        var statement = connection.createStatement();
        statement.setQueryTimeout(timeout);
        
        try {
            // Delete encryption keys first (foreign key constraint)
            statement.executeUpdate(
                "DELETE FROM encryption_keys WHERE file_id = '" + fileId + "'"
            );
            
            // Delete chunk locations (foreign key constraint)
            statement.executeUpdate(
                "DELETE FROM file_chunks WHERE file_id = '" + fileId + "'"
            );
            
            // Finally delete the main file record
            statement.executeUpdate(
                "DELETE FROM files WHERE file_id = '" + fileId + "'"
            );
            
            // If we got here without exceptions, commit the transaction
            connection.commit();
            System.out.println("Successfully deleted metadata for file: " + fileId);
            
        } catch (SQLException e) {
            // If anything goes wrong, roll back all changes
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
        
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            ResultSet rs = statement.executeQuery(
                "SELECT * FROM files"
            );
            
            while (rs.next()) {
                FileMetadata metadata = new FileMetadata(
                    rs.getString("file_id"),
                    rs.getString("file_name"),
                    rs.getString("owner_user"),
                    rs.getLong("total_size")
                );
                metadata.setShared(rs.getInt("is_shared") == 1);
                
                // Get chunk locations for this file
                ResultSet chunksRs = statement.executeQuery(
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
                
                files.add(metadata);
            }
            
        } catch (SQLException e) {
            System.err.println("Error retrieving files: " + e.getMessage());
            throw new RuntimeException("Database error", e);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
        
        return files;
    }
    
    /**
     * @brief delete table
     * @param tableName of type String
     */
    public void delTable(String tableName) throws ClassNotFoundException {
        try {
            // create a database connection
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
                // connection close failed.
                System.err.println(e.getMessage());
            }
        }
    }
    
    /**
     * @brief add data to the database method
     * @param user name of type String
     * @param password of type String
     */
    public void addDataToDB(String user, String password) throws InvalidKeySpecException, ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            System.out.println("Adding User: " + user + ", Password: " + password);
            statement.executeUpdate("insert into " + dataBaseTableName + " (name, password) values('" + user + "','" + generateSecurePassword(password) + "')");
        } catch (SQLException ex) {
            Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException ex) {
                Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (connection != null) {
                        connection.close();
                    }
                } catch (SQLException e) {
                    // connection close failed.
                    System.err.println(e.getMessage());
                }
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
                // read the result set
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
                // connection close failed.
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
            // Let's iterate through the java ResultSet
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
                // connection close failed.
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
    
    /* Method to generate the hash value */
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
    
    // Add these methods to your existing DB class
    public void saveFileMetadata(FileMetadata metadata) throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(fileName);
            var statement = connection.createStatement();
            statement.setQueryTimeout(timeout);
            
            // Add file metadata
            statement.executeUpdate(
                    "INSERT INTO files (file_id, file_name, owner_user, total_size, total_chunks, is_shared) " +
                            "VALUES ('" + metadata.getFileId() + "', '" + metadata.getFileName() + "', '" +
                            metadata.getOwnerUser() + "', " + metadata.getTotalSize() + ", " +
                            metadata.getTotalChunks() + ", " + (metadata.isShared() ? 1 : 0) + ")"
            );
            
            // Add chunk locations
            for (int i = 0; i < metadata.getTotalChunks(); i++) {
                String containerId = metadata.getContainerForChunk(i);
                if (containerId != null) {
                    statement.executeUpdate(
                            "INSERT INTO file_chunks (file_id, chunk_number, container_id) " +
                                    "VALUES ('" + metadata.getFileId() + "', " + i + ", '" + containerId + "')"
                    );
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
                
                // Get chunk locations
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
    
}

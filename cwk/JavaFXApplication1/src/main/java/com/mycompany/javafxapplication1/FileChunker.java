package com.mycompany.javafxapplication1;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.logging.*;

/**
 * Handles splitting files into chunks and reassembling them
 */
public class FileChunker {
    private static final Logger logger = Logger.getLogger(FileChunker.class.getName());
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024; // 1MB default
    
    public FileChunker() {
    }
    
    /**
     * Splits a file into chunks with checksums
     * @param file The file to split
     * @param chunkSize Size of each chunk in bytes
     * @return List of chunk metadata
     */
    public List<ChunkInfo> splitFile(File file, int chunkSize) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getPath());
        }
        
        List<ChunkInfo> chunks = new ArrayList<>();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("Failed to initialize checksum", e);
        }
        
        // Create a single FileEncryption instance for all chunks
        FileEncryption encryption = new FileEncryption();
        String encryptionKey = encryption.getKeyAsString();
        logger.info("Created encryption key for all chunks: " + encryptionKey);
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[chunkSize];
            int chunkNumber = 0;
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunkData;
                if (bytesRead < chunkSize) {
                    chunkData = Arrays.copyOf(buffer, bytesRead);
                } else {
                    chunkData = buffer.clone();
                }
                
                // Encrypt chunk using the same encryption instance
                byte[] encryptedData = encryption.encryptData(chunkData);
                if (encryptedData == null) {
                    throw new IOException("Failed to encrypt chunk " + chunkNumber);
                }
                
                // Calculate checksum on encrypted data
                md.reset();
                md.update(encryptedData);
                String checksum = Base64.getEncoder().encodeToString(md.digest());
                
                ChunkInfo chunk = new ChunkInfo(
                        chunkNumber,
                        bytesRead,
                        checksum,
                        encryptionKey,  // Use the same key for all chunks
                        encryptedData
                );
                
                chunks.add(chunk);
                chunkNumber++;
                
                logger.info(String.format("Created chunk %d: size=%d bytes, checksum=%s",
                        chunkNumber, bytesRead, checksum));
            }
        }
        
        return chunks;
    }
    
    
    
    /**
     * Reassembles chunks back into a file, verifying checksums
     * @param chunks List of chunks to reassemble
     * @param outputFile The file to write to
     * @throws IOException if reassembly fails
     */
    public void reassembleFile(List<ChunkInfo> chunks, File outputFile) throws IOException {
        chunks.sort(Comparator.comparingInt(ChunkInfo::getNumber));
        logger.info("Starting file reassembly with " + chunks.size() + " chunks");
        
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("Failed to initialize checksum", e);
        }
        
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (ChunkInfo chunk : chunks) {
                logger.info("Processing chunk " + chunk.getNumber());
                
                // Verify checksum of encrypted data
                md.reset();
                md.update(chunk.getData());
                String calculatedChecksum = Base64.getEncoder().encodeToString(md.digest());
                
                if (!calculatedChecksum.equals(chunk.getChecksum())) {
                    logger.severe("Chunk " + chunk.getNumber() + " failed checksum verification");
                    logger.severe("Expected: " + chunk.getChecksum());
                    logger.severe("Got: " + calculatedChecksum);
                    throw new IOException("Chunk " + chunk.getNumber() + " failed checksum verification");
                }
                
                logger.info("Chunk " + chunk.getNumber() + " passed checksum verification");
                
                // Get encryption key and validate
                String key = chunk.getEncryptionKey();
                if (key == null || key.trim().isEmpty()) {
                    logger.severe("Missing encryption key for chunk " + chunk.getNumber());
                    throw new IOException("Missing encryption key for chunk " + chunk.getNumber());
                }
                
                logger.info("Using encryption key for chunk " + chunk.getNumber() + ": " + key);
                
                try {
                    // Create new encryption instance for this chunk
                    FileEncryption chunkEncryption = FileEncryption.fromKey(key);
                    byte[] decryptedData = chunkEncryption.decryptData(chunk.getData());
                    
                    if (decryptedData == null) {
                        logger.severe("Decryption returned null for chunk " + chunk.getNumber());
                        throw new IOException("Failed to decrypt chunk " + chunk.getNumber());
                    }
                    
                    fos.write(decryptedData);
                    fos.flush();
                    
                    logger.info(String.format("Successfully reassembled chunk %d", chunk.getNumber()));
                    
                } catch (Exception e) {
                    logger.severe("Error decrypting chunk " + chunk.getNumber() + ": " + e.getMessage());
                    throw new IOException("Failed to decrypt chunk " + chunk.getNumber(), e);
                }
            }
        }
    }
    
    /**
     * Gets the optimal chunk size based on file size
     * @param fileSize Total file size in bytes
     * @return Recommended chunk size in bytes
     */
    public int getOptimalChunkSize(long fileSize) {
        if (fileSize < DEFAULT_CHUNK_SIZE) {
            return (int) fileSize;  // Single chunk for small files
        }
        
        // Aim for around 10 chunks, but no larger than 10MB per chunk
        int chunkSize = (int) (fileSize / 10);
        return Math.min(chunkSize, 10 * DEFAULT_CHUNK_SIZE);
    }
    
    /**
     * Class to hold chunk metadata and data
     */
    public static class ChunkInfo {
        private final int number;
        private final int size;
        private final String checksum;
        private final String encryptionKey;
        private final byte[] data;
        
        public ChunkInfo(int number, int size, String checksum,
                String encryptionKey, byte[] data) {
            this.number = number;
            this.size = size;
            this.checksum = checksum;
            this.encryptionKey = encryptionKey;
            this.data = data;
        }
        
        // Getters
        public int getNumber() { return number; }
        public int getSize() { return size; }
        public String getChecksum() { return checksum; }
        public String getEncryptionKey() { return encryptionKey; }
        public byte[] getData() { return data; }
    }
}
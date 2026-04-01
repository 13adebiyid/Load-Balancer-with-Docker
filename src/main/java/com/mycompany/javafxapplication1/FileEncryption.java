package com.mycompany.javafxapplication1;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Handles encryption and decryption of file chunks using AES
 */
public class FileEncryption {
    private static final Logger logger = Logger.getLogger(FileEncryption.class.getName());
    private SecretKey secretKey;
    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/ECB/PKCS5Padding";
    
    /**
     * Constructor generates a new encryption key
     */
    public FileEncryption() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGen.init(128, new SecureRandom());
            this.secretKey = keyGen.generateKey();
            logger.info("Created new encryption key: " + getKeyAsString());
        } catch (Exception e) {
            logger.severe("Error creating encryption key: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Constructor loads an existing encryption key from a Base64 string
     */
    public FileEncryption(String keyString) {
        try {
            byte[] keyData = Base64.getDecoder().decode(keyString);
            this.secretKey = new SecretKeySpec(keyData, KEY_ALGORITHM);
            logger.info("Loaded existing encryption key: " + keyString);
        } catch (Exception e) {
            logger.severe("Error loading encryption key: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Encrypts a chunk of file data
     */
    public byte[] encryptData(byte[] data) {
        if (data == null || data.length == 0) {
            logger.warning("Attempted to encrypt null or empty data");
            return null;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedData = cipher.doFinal(data);
            logger.info("Successfully encrypted " + data.length + " bytes with key: " + getKeyAsString());
            return encryptedData;
        } catch (Exception e) {
            logger.severe("Error encrypting data with key " + getKeyAsString() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Decrypts a chunk of file data
     */
    public byte[] decryptData(byte[] encryptedData) {
        if (encryptedData == null || encryptedData.length == 0) {
            logger.warning("Attempted to decrypt null or empty data");
            return null;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedData = cipher.doFinal(encryptedData);
            logger.info("Successfully decrypted " + encryptedData.length + " bytes with key: " + getKeyAsString());
            return decryptedData;
        } catch (Exception e) {
            logger.severe("Error decrypting data with key " + getKeyAsString() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Gets encryption key as Base64 string
     */
    public String getKeyAsString() {
        if (secretKey == null) return "null-key";
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
    
    /**
     * Create FileEncryption instance from a stored key
     */
    public static FileEncryption fromKey(String keyString) {
        if (keyString == null || keyString.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key cannot be null or empty");
        }
        logger.info("Creating FileEncryption instance from key: " + keyString);
        return new FileEncryption(keyString);
    }
}

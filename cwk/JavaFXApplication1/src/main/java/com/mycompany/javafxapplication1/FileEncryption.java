package com.mycompany.javafxapplication1;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Handles encryption and decryption of file chunks using AES
 * @author student
 */
public class FileEncryption {
    private SecretKey secretKey;
    private static final String ALGORITHM = "AES";
    
    /**
     * Constructor - generates a new encryption key
     */
    public FileEncryption() {
        try {
            // Create key generator and initialize it
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            SecureRandom random = new SecureRandom();
            keyGen.init(128, random);
            
            // Generate the secret key
            this.secretKey = keyGen.generateKey();
            System.out.println("Encryption key generated successfully");
            
        } catch (Exception e) {
            System.out.println("Error creating encryption key: " + e.getMessage());
        }
    }
    
    /**
     * Encrypts a chunk of file data
     * @param data The data to encrypt
     * @return The encrypted data
     */
    public byte[] encryptData(byte[] data) {
        try {
            // Create and initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            // Perform encryption
            return cipher.doFinal(data);
            
        } catch (Exception e) {
            System.out.println("Error encrypting data: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Decrypts a chunk of file data
     * @param encryptedData The data to decrypt
     * @return The decrypted data
     */
    public byte[] decryptData(byte[] encryptedData) {
        try {
            // Create and initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            // Perform decryption
            return cipher.doFinal(encryptedData);
            
        } catch (Exception e) {
            System.out.println("Error decrypting data: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the encryption key as a string for storage
     * @return Base64 encoded string of the key
     */
    public String getKeyAsString() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
    
    /**
     * Creates an encryption instance from a stored key
     * @param keyString Base64 encoded key string
     * @return New FileEncryption instance with the specified key
     */
    public static FileEncryption fromKey(String keyString) {
        try {
            // Convert the Base64 string back to a key
            byte[] keyData = Base64.getDecoder().decode(keyString);
            SecretKey key = new SecretKeySpec(keyData, ALGORITHM);
            
            // Create new instance with this key
            FileEncryption encryption = new FileEncryption();
            encryption.secretKey = key;
            return encryption;
            
        } catch (Exception e) {
            System.out.println("Error creating encryption from key: " + e.getMessage());
            return null;
        }
    }
}
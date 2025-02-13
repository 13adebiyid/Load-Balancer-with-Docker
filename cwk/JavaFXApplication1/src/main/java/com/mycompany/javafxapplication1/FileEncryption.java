package com.mycompany.javafxapplication1;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Handles encryption and decryption of file chunks using AES
 */
public class FileEncryption {
    private SecretKey secretKey;
    private static final String ALGORITHM = "AES";
    
    /**
     * Constructor - generates a new encryption key (only use for new files)
     */
    public FileEncryption() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(128);
            this.secretKey = keyGen.generateKey();
        } catch (Exception e) {
            System.out.println("Error creating encryption key: " + e.getMessage());
        }
    }
    
    /**
     * Constructor - loads an existing encryption key from a Base64 string
     */
    public FileEncryption(String keyString) {
        try {
            byte[] keyData = Base64.getDecoder().decode(keyString);
            this.secretKey = new SecretKeySpec(keyData, ALGORITHM);
        } catch (Exception e) {
            System.out.println("Error loading encryption key: " + e.getMessage());
        }
    }
    
    /**
     * Encrypts a chunk of file data
     */
    public byte[] encryptData(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            System.out.println("Error encrypting data: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Decrypts a chunk of file data
     */
    public byte[] decryptData(byte[] encryptedData) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            System.out.println("Error decrypting data: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the encryption key as a Base64 string
     */
    public String getKeyAsString() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
    
    /**
     * Creates a FileEncryption instance from a stored key
     */
    public static FileEncryption fromKey(String keyString) {
        return new FileEncryption(keyString);
    }
}

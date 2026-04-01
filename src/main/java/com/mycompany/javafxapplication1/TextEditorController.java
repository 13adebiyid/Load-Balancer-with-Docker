package com.mycompany.javafxapplication1;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.event.ActionEvent;

public class TextEditorController {
    @FXML private TextArea contentArea;
    @FXML private TextField fileNameField;
    @FXML private Button saveButton;
    @FXML private Label statusLabel;
    
    private FileMetadata currentFile;
    private String currentUser;
    private DB database;
    private boolean hasWriteAccess;
    private FileOperationsController fileOps;
    private boolean isNewFile;
    
    public void initialize() {
        database = new DB();
        fileOps = new FileOperationsController() {
            //Complications with progress bar so overided the methods to retun nothning
            @Override
            protected void updateProgress(double progress) {
            }
            
            @Override
            protected void clearProgress() {
            }
            
            @Override
            protected void showAlert(Alert.AlertType type, String title, String message) {
                TextEditorController.this.showError(title, message);
            }
        };
        
        try {
            fileOps.initializeComponents();
        } catch (Exception e) {
            showError("Error", "Failed to initialize components: " + e.getMessage());
            e.printStackTrace();
        }
        
        contentArea.textProperty().addListener((obs, oldText, newText) -> {
            if (hasWriteAccess) {
                saveButton.setDisable(false);
            }
        });
    }
    
    public void setCurrentUser(String username) {
        this.currentUser = username;
        fileOps.setCurrentUser(username);
    }
    
    public void loadFile(FileMetadata file) {
        this.currentFile = file;
        this.isNewFile = false;
        fileNameField.setText(file.getFileName());
        
        try {
            boolean canRead = database.checkFilePermission(file.getFileId(), currentUser, "read");
            hasWriteAccess = database.checkFilePermission(file.getFileId(), currentUser, "write");
            
            if (!canRead) {
                showError("Access Denied", "You don't have permission to read this file");
                return;
            }
            
            contentArea.setEditable(hasWriteAccess);
            saveButton.setDisable(!hasWriteAccess);
            
            File tempFile = File.createTempFile("edit_", ".tmp");
            try {
                fileOps.downloadAndAssembleFile(file.getFileId(), tempFile);
                String content = new String(Files.readAllBytes(tempFile.toPath()));
                contentArea.setText(content);
                statusLabel.setText("File loaded successfully");
            } finally {
                tempFile.delete();
            }
            
        } catch (Exception e) {
            showError("Error", "Failed to load file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    public void handleSave() {
        if (!hasWriteAccess) {
            showError("Access Denied", "You don't have write permission for this file");
            return;
        }
        
        try {
            File tempFile = File.createTempFile("save_", ".tmp");
            Files.write(tempFile.toPath(), contentArea.getText().getBytes());
            
            currentFile.setTotalSize(tempFile.length());
            
            database.deleteEncryptionKeys(currentFile.getFileId());
            
            try {
                fileOps.uploadFileInChunks(tempFile, currentFile.getFileId());
                
                database.saveFileMetadata(currentFile);
                
                statusLabel.setText("File saved successfully");
                saveButton.setDisable(true);
                
            } catch (Exception e) {
                showError("Save Failed", "Error during file upload: " + e.getMessage());
                e.printStackTrace();
                return;
            }
            
            tempFile.delete();
            
        } catch (Exception e) {
            showError("Save Failed", e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleNew() {
        String newFileName = fileNameField.getText().trim();
        if (newFileName.isEmpty()) {
            showError("Invalid Name", "Please enter a file name");
            return;
        }
        
        String fileId = UUID.randomUUID().toString();
        currentFile = new FileMetadata(fileId, newFileName, currentUser, 0);
        hasWriteAccess = true;
        isNewFile = true;  
        
        contentArea.clear();
        contentArea.setEditable(true);
        saveButton.setDisable(false);
        
        try {
            database.saveFileMetadata(currentFile);
            statusLabel.setText("New file created");
        } catch (ClassNotFoundException e) {
            showError("Error", "Failed to create file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
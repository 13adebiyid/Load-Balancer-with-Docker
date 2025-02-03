package com.mycompany.javafxapplication1;

import java.io.*;
import java.util.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class FileOperationsController {
    @FXML
    private Button uploadBtn;
    @FXML
    private Button downloadBtn;
    @FXML
    private Button backBtn;
    @FXML
    private TextField fileTextField;
    @FXML
    private ProgressBar progressBar;
    
    private Map<String, List<String>> fileContainerMap;
    private LoadBalancer loadBalancer;
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks
    private LoadBalancerClient loadBalancerClient;
    
    
    @FXML
    public void initialize() {
        // Initialize your components
        fileTextField.setText("No file selected");
        progressBar.setProgress(0.0);
        
        // Initialize the load balancer with storage containers
        loadBalancerClient = new LoadBalancerClient("localhost", 8080);
        fileContainerMap = new HashMap<>();
        loadBalancer = new LoadBalancer();
        
        // Add storage containers (you'll need to configure these based on your Docker setup)
        for (int i = 1; i <= 4; i++) {
            FileStorageContainer container = new FileStorageContainer(
                    "container-" + i,
                    "/storage/container" + i
            );
            loadBalancer.addContainer(container);
        }
        
    }
    
    @FXML
    private void uploadBtnHandler(ActionEvent event) {
        System.out.println("Upload button clicked - starting file selection process");
        Stage primaryStage = (Stage) uploadBtn.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        
        if (selectedFile != null) {
            fileTextField.setText(selectedFile.getAbsolutePath());
            System.out.println("File selected for upload: " + selectedFile.getAbsolutePath());
            
            // Generate a unique ID for this file
            String fileId = UUID.randomUUID().toString();
            System.out.println("Generated file ID: " + fileId);
            
            try {
                System.out.println("Starting file upload process...");
                uploadFileInChunks(selectedFile, fileId);
                dialogue("File Upload", "File uploaded successfully across containers");
            } catch (IOException e) {
                dialogue("Upload Error", "Failed to upload file: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("No file was selected");
        }
    }
    
    /**
     * Store file chunk location information when uploading
     * Add this to your existing upload method
     */
    private void recordChunkLocation(String fileId, int chunkNumber, String containerId) {
        fileContainerMap.computeIfAbsent(fileId, k -> new ArrayList<>())
                .add(containerId);
    }
    
    /**
     * Get the fileId for a given file path
     * This is a simple implementation - you might want to store this mapping in your database
     */
    private String getFileIdForPath(String path) {
        // For now, just return the path as the ID
        // In a real implementation, you'd look this up in your database
        return path;
    }
    
    private void uploadFileInChunks(File file, String fileId) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[CHUNK_SIZE];
        int chunkNumber = 0;
        int bytesRead;
        
        while ((bytesRead = fis.read(buffer)) != -1) {
            byte[] chunk = bytesRead < buffer.length ?
                    new byte[bytesRead] : buffer;
            
            if (bytesRead < buffer.length) {
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
            }
            
            try {
                String containerId = loadBalancerClient.uploadFileChunk(
                        fileId, chunkNumber, chunk
                );
                // Record where this chunk was stored
                recordChunkLocation(fileId, chunkNumber, containerId);
                
                System.out.println("Chunk " + chunkNumber + " stored in container " + containerId);
            } catch (ClassNotFoundException e) {
                throw new IOException("Error uploading chunk", e);
            }
            
            chunkNumber++;
            updateProgress(chunkNumber * buffer.length / (double) file.length());
        }
        System.out.println(String.format("Processing chunk %d (size: %d bytes)",chunkNumber,bytesRead));
        
        fis.close();
    }
    
    @FXML
    private void downloadBtnHandler(ActionEvent event) {
        System.out.println("Download button clicked - starting save location selection");
        Stage primaryStage = (Stage) downloadBtn.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        File saveFile = fileChooser.showSaveDialog(primaryStage);
        
        if (saveFile != null) {
            fileTextField.setText(saveFile.getAbsolutePath());
            System.out.println("File selected for download: " + saveFile.getAbsolutePath());
            
            try {
                // Get the fileId from our stored map
                String fileId = getFileIdForPath(saveFile.getAbsolutePath());
                if (fileId == null) {
                    dialogue("Error", "File not found in the system");
                    return;
                }
                
                downloadAndAssembleFile(fileId, saveFile);
                dialogue("File Download", "File downloaded successfully");
                
            } catch (Exception e) {
                dialogue("Download Error", "Failed to download file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Downloads all chunks of a file and reassembles them
     * @param fileId The unique identifier of the file
     * @param outputFile The file to save the assembled data to
     */
    private void downloadAndAssembleFile(String fileId, File outputFile)
            throws IOException, ClassNotFoundException {
        
        // Get the number of chunks for this file
        List<String> containerList = fileContainerMap.get(fileId);
        if (containerList == null) {
            throw new IOException("No chunk information found for file");
        }
        
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (int chunkNumber = 0; chunkNumber < containerList.size(); chunkNumber++) {
                // Download each chunk
                byte[] chunkData = loadBalancerClient.downloadFileChunk(fileId, chunkNumber);
                
                // Write the chunk to the output file
                fos.write(chunkData);
                
                // Update progress bar
                updateProgress((chunkNumber + 1.0) / containerList.size());
                System.out.println(String.format("Retrieving chunk %d of %d",chunkNumber + 1,containerList.size()));
            }
            
            fos.flush();
        }
    }
    
    private void updateProgress(double progress) {
        progressBar.setProgress(progress);
    }
    
    @FXML
    private void backBtnHandler(ActionEvent event) {
        Stage secondaryStage = new Stage();
        Stage primaryStage = (Stage) backBtn.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("secondary.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Show Users");
            secondaryStage.show();
            primaryStage.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void dialogue(String headerMsg, String contentMsg) {
        Stage secondaryStage = new Stage();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Confirmation Dialog");
        alert.setHeaderText(headerMsg);
        alert.setContentText(contentMsg);
        Optional<ButtonType> result = alert.showAndWait();
    }
}
package com.mycompany.javafxapplication1;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Controller for handling file operations in the distributed storage system.
 * Implements file uploads with chunking, downloads with reassembly,
 * and provides user interface feedback.
 */
public class FileOperationsController {
    // FXML injected controls
    @FXML private TextField fileTextField;
    @FXML private Button uploadBtn;
    @FXML private Button downloadBtn;
    @FXML private Button backBtn;
    @FXML private ProgressBar progressBar;
    @FXML private TableView<FileMetadata> filesTable;
    @FXML private TableColumn<FileMetadata, String> fileNameColumn;
    @FXML private TableColumn<FileMetadata, String> ownerColumn;
    @FXML private TableColumn<FileMetadata, String> sizeColumn;
    
    // Constants
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks

    
    // Instance variables
    private Map<String, List<String>> fileContainerMap;
    private LoadBalancerClient loadBalancerClient;
    private Map<String, String> filePathToIdMap;
    private DB database;
    private String currentUser;
    private volatile boolean operationCancelled;
    
    /**
     * Initializes the controller after FXML loading
     */
    @FXML
    public void initialize() {
        filePathToIdMap = new HashMap<>();
        fileContainerMap = new HashMap<>();
        database = new DB();
        
        initializeUI();
        setupTableColumns();
        refreshFilesList();
        
        loadBalancerClient = new LoadBalancerClient("localhost", 8080);
    }
    
    /**
     * Sets up initial UI state
     */
    private void initializeUI() {
        fileTextField.setText("No file selected");
        progressBar.setProgress(0.0);
        
        // Add tooltips for buttons
        uploadBtn.setTooltip(new Tooltip("Upload a new file"));
        downloadBtn.setTooltip(new Tooltip("Download selected file"));
        backBtn.setTooltip(new Tooltip("Return to main menu"));
        
        // Setup table selection behavior
        filesTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        filesTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                FileMetadata selectedFile = filesTable.getSelectionModel().getSelectedItem();
                if (selectedFile != null) {
                    initiateDownload(selectedFile);
                }
            }
        });
    }
    
    /**
     * Sets up the table columns with their cell value factories
     */
    private void setupTableColumns() {
        fileNameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getFileName()));
        
        ownerColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOwnerUser()));
        
        sizeColumn.setCellValueFactory(data -> {
            long bytes = data.getValue().getTotalSize();
            String readableSize;
            
            if (bytes < 1024) {
                readableSize = bytes + " B";
            } else {
                int exp = (int) (Math.log(bytes) / Math.log(1024));
                String pre = "KMGTPE".charAt(exp-1) + "";
                readableSize = String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
            }
            
            return new SimpleStringProperty(readableSize);
        });
        
        
        
        // Add context menu for files
        filesTable.setRowFactory(tv -> {
            TableRow<FileMetadata> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem downloadItem = new MenuItem("Download");
            downloadItem.setOnAction(event -> {
                FileMetadata file = row.getItem();
                if (file != null) {
                    initiateDownload(file);
                }
            });
            
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(event -> {
                FileMetadata file = row.getItem();
                if (file != null && confirmDelete(file)) {
                    deleteFile(file);
                }
            });
            
            contextMenu.getItems().addAll(downloadItem, deleteItem);
            
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );
            
            return row;
        });
    }
    
    /**
     * Refreshes the files list from the database
     */
    private void refreshFilesList() {
    try {
        System.out.println("Refreshing files list...");
        List<FileMetadata> files = database.getAllFiles();
        System.out.println("Retrieved " + files.size() + " files from database");
        
        Platform.runLater(() -> {
            try {
                filesTable.getItems().clear();
                filesTable.getItems().addAll(files);
                filesTable.refresh();
                System.out.println("Updated table view with " + filesTable.getItems().size() + " items");
            } catch (Exception e) {
                System.err.println("Error updating table: " + e.getMessage());
                e.printStackTrace();
            }
        });
    } catch (ClassNotFoundException e) {
        System.err.println("Failed to load files: " + e.getMessage());
        e.printStackTrace();
        Platform.runLater(() ->
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load files: " + e.getMessage())
        );
    }
}
    
    /**
     * Handles file upload button click
     */
    @FXML
private void uploadBtnHandler(ActionEvent event) {
    if (currentUser == null || currentUser.isEmpty()) {
        showAlert(Alert.AlertType.ERROR, "Error", "Please log in to upload files");
        return;
    }
    
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select File to Upload");
    File selectedFile = fileChooser.showOpenDialog(uploadBtn.getScene().getWindow());
    
    if (selectedFile != null) {
        fileTextField.setText(selectedFile.getAbsolutePath());
        String fileId = UUID.randomUUID().toString();
        
        setControlsEnabled(false);
        operationCancelled = false;
        
        Task<Void> uploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                uploadFileInChunks(selectedFile, fileId);
                return null;
            }
        };

        uploadTask.setOnSucceeded(e -> {
            setControlsEnabled(true);
            clearProgress();
            refreshFilesList(); // Refresh list after successful upload
        });

        uploadTask.setOnFailed(e -> {
            setControlsEnabled(true);
            clearProgress();
            Throwable exc = uploadTask.getException();
            showAlert(Alert.AlertType.ERROR, "Upload Failed", 
                     exc != null ? exc.getMessage() : "Unknown error");
        });

        new Thread(uploadTask).start();
    }
}
    
    /**
     * Handles file download button click
     */
    @FXML
    private void downloadBtnHandler(ActionEvent event) {
        FileMetadata selectedFile = filesTable.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Required",
                    "Please select a file to download");
            return;
        }
        
        initiateDownload(selectedFile);
    }
    
    /**
     * Initiates the file download process
     */
    private void initiateDownload(FileMetadata metadata) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        fileChooser.setInitialFileName(metadata.getFileName());
        File saveFile = fileChooser.showSaveDialog(downloadBtn.getScene().getWindow());
        
        if (saveFile != null) {
            fileTextField.setText(saveFile.getAbsolutePath());
            setControlsEnabled(false);
            operationCancelled = false;
            
            new Thread(() -> {
                try {
                    downloadAndAssembleFile(metadata.getFileId(), saveFile);
                    
                    if (!operationCancelled) {
                        Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Success",
                                    "File downloaded successfully");
                            setControlsEnabled(true);
                            clearProgress();
                        });
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "Download Error",
                                "Failed to download: " + e.getMessage());
                        setControlsEnabled(true);
                        clearProgress();
                    });
                }
            }).start();
        }
    }
    
    /**
     * Uploads a file in chunks using the load balancer
     */
    private void uploadFileInChunks(File file, String fileId) throws IOException {
    long totalSize = file.length();
    System.out.println("Starting upload of file: " + file.getName() +
            " (Size: " + totalSize + " bytes)");
    
    try (FileInputStream fis = new FileInputStream(file)) {
        byte[] buffer = new byte[CHUNK_SIZE];
        int chunkNumber = 0;
        long bytesProcessed = 0;
        int bytesRead;
        
        while ((bytesRead = fis.read(buffer)) != -1 && !operationCancelled) {
            byte[] chunk;
            if (bytesRead < CHUNK_SIZE) {
                chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
            } else {
                chunk = buffer.clone();
            }
            
            System.out.println("Uploading chunk " + chunkNumber +
                    " (size: " + bytesRead + " bytes)");
            
            boolean chunkUploaded = false;
            int retries = 0;
            
            while (!chunkUploaded && !operationCancelled) {
                try {
                    String containerId = loadBalancerClient.uploadFileChunk(
                            fileId, chunkNumber, chunk
                    );
                    
                    if (containerId != null) {
                        recordChunkLocation(fileId, chunkNumber, containerId);
                        bytesProcessed += bytesRead;
                        chunkUploaded = true;
                        
                        final double progress = (double) bytesProcessed / totalSize;
                        Platform.runLater(() -> updateProgress(progress));
                        
                        // Simulate network delay (30-90 seconds)
                        Thread.sleep(30000 + new Random().nextInt(60000));
                    }
                } catch (Exception e) {
                    System.err.println("Error uploading chunk " + chunkNumber +
                            ": " + e.getMessage());
                    
                }
            }
            
            if (operationCancelled) {
                System.out.println("Upload cancelled by user");
                break;
            }
            
            chunkNumber++;
        }
        
        if (!operationCancelled) {
            FileMetadata metadata = new FileMetadata(fileId, file.getName(), currentUser, totalSize);
            metadata.setTotalChunks(chunkNumber);
            
            for (int i = 0; i < chunkNumber; i++) {
                if (fileContainerMap.containsKey(fileId) && i < fileContainerMap.get(fileId).size()) {
                    metadata.addChunkLocation(i, fileContainerMap.get(fileId).get(i));
                }
            }
            
            try {
                database.saveFileMetadata(metadata);
                System.out.println("Saved metadata for file: " + file.getName());
            } catch (ClassNotFoundException ex) {
                throw new IOException("Failed to save metadata: " + ex.getMessage());
            }
        }
    }}
    
        // Helper method to show alerts on the JavaFX Application Thread
    private void showAlert(Alert.AlertType type, String title, String message) {
        if (Platform.isFxApplicationThread()) {
            showAlertImpl(type, title, message);
        } else {
            Platform.runLater(() -> showAlertImpl(type, title, message));
        }
    }

    // Implementation of alert display
    private void showAlertImpl(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Downloads and reassembles a file from chunks
     */
    private void downloadAndAssembleFile(String fileId, File outputFile)
        throws IOException, ClassNotFoundException {
    
    FileMetadata metadata = database.getFileMetadata(fileId);
    if (metadata == null) {
        throw new IOException("File metadata not found");
    }
    
    System.out.println("\nDebug info for download:");
    System.out.println("File ID: " + fileId);
    System.out.println("File name: " + metadata.getFileName());
    System.out.println("Total chunks: " + metadata.getTotalChunks());
    System.out.println("Total size: " + metadata.getTotalSize() + " bytes");
    System.out.println("Owner: " + metadata.getOwnerUser());
    System.out.println("Chunk locations:");
    for (int i = 0; i < metadata.getTotalChunks(); i++) {
        String containerId = metadata.getContainerForChunk(i);
        System.out.println("Chunk " + i + ": Container " + containerId);
    }
    
    System.out.println("Starting download of file: " + metadata.getFileName() +
            " (Total chunks: " + metadata.getTotalChunks() + ")");
    
    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
        long bytesProcessed = 0;
        
        for (int chunkNumber = 0; chunkNumber < metadata.getTotalChunks(); chunkNumber++) {
            if (operationCancelled) {
                System.out.println("Download cancelled by user");
                break;
            }
            
            System.out.println("Downloading chunk " + chunkNumber);
            boolean chunkDownloaded = false;
            int retries = 0;
            
            while (!chunkDownloaded && !operationCancelled) {
                try {
                    byte[] chunkData = loadBalancerClient.downloadFileChunk(
                            fileId, chunkNumber
                    );
                    
                    if (chunkData != null) {
                        // Write the chunk data with proper flushing
                        fos.write(chunkData);
                        fos.flush();
                        fos.getFD().sync();  // Force write to disk
                        
                        bytesProcessed += chunkData.length;
                        chunkDownloaded = true;
                        
                        final double progress = (double) bytesProcessed / metadata.getTotalSize();
                        Platform.runLater(() -> updateProgress(progress));
                        
                        System.out.println("Downloaded chunk " + chunkNumber +
                                " (size: " + chunkData.length + " bytes)");
                    }
                } catch (Exception e) {
                    System.err.println("Error downloading chunk " + chunkNumber +
                            ": " + e.getMessage());
                    
                }
            }
        }
        
        // Final flush and sync after all chunks are written
        fos.flush();
        fos.getFD().sync();
        
        if (!operationCancelled) {
            System.out.println("Download complete. Total bytes: " + bytesProcessed);
        }
    }}
    
    /**
     * Returns to the main menu
     */
    @FXML
    private void backBtnHandler(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("secondary.fxml"));
            Parent root = loader.load();
            SecondaryController controller = loader.getController();
            controller.initialise(new String[]{currentUser, ""});  // Pass current user
            
            Scene scene = new Scene(root, 640, 480);
            Stage currentStage = (Stage) backBtn.getScene().getWindow();
            Stage newStage = new Stage();
            newStage.setScene(scene);
            newStage.setTitle("Show Users");
            newStage.show();
            currentStage.close();
            
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Navigation Error",
                    "Failed to return to main menu: " + e.getMessage());
            e.printStackTrace();
        }
    }
   
    
    /**
     * Updates the progress bar
     */
    private void updateProgress(double progress) {
        if (Platform.isFxApplicationThread()) {
            progressBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
        } else {
            Platform.runLater(() -> progressBar.setProgress(Math.min(1.0, Math.max(0.0, progress))));
        }
    }
    
    /**
     * Enables or disables UI controls during operations
     */
    private void setControlsEnabled(boolean enabled) {
        if (Platform.isFxApplicationThread()) {
            setControlsEnabledImpl(enabled);
        } else {
            Platform.runLater(() -> setControlsEnabledImpl(enabled));
        }
    }
    
    /**
     * Resets the progress bar
     */
    private void clearProgress() {
    if (Platform.isFxApplicationThread()) {
        clearProgressImpl();
    } else {
        Platform.runLater(this::clearProgressImpl);
    }
}

private void clearProgressImpl() {
    progressBar.setProgress(0.0);
    fileTextField.setText("No file selected");
}
    
    /**
     * Records the location of a file chunk
     */
    private void recordChunkLocation(String fileId, int chunkNumber, String containerId) {
        List<String> containerList = fileContainerMap.computeIfAbsent(fileId, k -> new ArrayList<>());
        while (containerList.size() <= chunkNumber) {
            containerList.add(null);
        }
        containerList.set(chunkNumber, containerId);
    }
    
    /**
     * Confirms file deletion with user
     */
    private boolean confirmDelete(FileMetadata file) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete File");
        alert.setContentText("Are you sure you want to delete " + file.getFileName() + "?");
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    /**
     * Deletes a file and its chunks from storage
     */
    private void deleteFile(FileMetadata file) {
        setControlsEnabled(false);
        
        new Thread(() -> {
            try {
                // Delete file chunks from storage containers
                List<String> containers = fileContainerMap.get(file.getFileId());
                if (containers != null) {
                    for (String containerId : containers) {
                        // Implement actual deletion from containers here
                        System.out.println("Deleting chunks from container: " + containerId);
                    }
                }
                
                // Delete metadata from database
                database.deleteFileMetadata(file.getFileId());
                
                Platform.runLater(() -> {
                    refreshFilesList();
                    setControlsEnabled(true);
                    showAlert(Alert.AlertType.INFORMATION, "Success",
                            "File deleted successfully");
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setControlsEnabled(true);
                    showAlert(Alert.AlertType.ERROR, "Delete Error",
                            "Failed to delete file: " + e.getMessage());
                });
            }
        }).start();
    }

    private void setControlsEnabledImpl(boolean enabled) {
        uploadBtn.setDisable(!enabled);
        downloadBtn.setDisable(!enabled);
        filesTable.setDisable(!enabled);
    }
    
    /**
     * Sets the current user for file ownership
     */
    public void setCurrentUser(String username) {
        this.currentUser = username;
    }
    
    /**
     * Cancels the current operation
     */
    public void cancelOperation() {
        operationCancelled = true;
    }
}
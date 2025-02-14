package com.mycompany.javafxapplication1;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
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
    @FXML private Button testScalingBtn;//remove
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
    
    public void initializeDatabase() throws ClassNotFoundException {
        database = new DB();
        database.initializeDatabase();
    }
    
    public void initializeComponents() throws ClassNotFoundException {
        database = new DB();
        database.initializeDatabase();
        
        loadBalancerClient = new LoadBalancerClient("localhost", 8080);
        
        FileLockManager lockManager = FileLockManager.getInstance();
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
    
    @FXML
    private void openRemoteTerminal() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("RemoteTerminal.fxml"));
            Parent root = loader.load();
            
            Stage terminalStage = new Stage();
            terminalStage.setTitle("Remote Container Terminal");
            terminalStage.setScene(new Scene(root, 800, 600));
            terminalStage.show();
            
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to open remote terminal: " + e.getMessage());
        }
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
        
        
        
        // context menu for files
        filesTable.setRowFactory(tv -> {
            TableRow<FileMetadata> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem remoteTerminalItem = new MenuItem("Remote Terminal");
            remoteTerminalItem.setOnAction(event -> openRemoteTerminal());
            
            MenuItem downloadItem = new MenuItem("Download");
            downloadItem.setOnAction(event -> {
                FileMetadata file = row.getItem();
                if (file != null) {
                    initiateDownload(file);
                }
            });
            
            // Edit menu item
            MenuItem editItem = new MenuItem("Edit");
            editItem.setOnAction(event -> {
                FileMetadata file = row.getItem();
                if (file != null) {
                    openTextEditor(file);
                }
            });
            
            // Share menu item
            MenuItem shareItem = new MenuItem("Share");
            shareItem.setOnAction(event -> {
                FileMetadata file = row.getItem();
                if (file != null) {
                    // Only allow sharing if user is the owner
                    if (file.getOwnerUser().equals(currentUser)) {
                        showShareDialog(file);
                    } else {
                        showAlert(Alert.AlertType.WARNING, "Access Denied",
                                "Only the file owner can modify sharing settings");
                    }
                }
            });
            
            // Show Access menu item
            MenuItem accessItem = new MenuItem("Show Access");
            accessItem.setOnAction(event -> {
                FileMetadata file = row.getItem();
                if (file != null) {
                    showAccessList(file);
                }
            });
            
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(event -> {
                FileMetadata file = row.getItem();
                if (file != null && confirmDelete(file)) {
                    deleteFile(file);
                }
            });
            
            contextMenu.getItems().addAll(downloadItem, shareItem, accessItem, deleteItem);
            
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
     * Shows the share dialog for setting file permissions
     */
    private void showShareDialog(FileMetadata file) {
        // Create custom dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Share " + file.getFileName());
        dialog.setHeaderText("Choose who to share with and their permissions");
        
        // Create the dialog content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        // Create user dropdown
        ComboBox<String> userComboBox = new ComboBox<>();
        try {
            // Get all users except the file owner
            ObservableList<User> users = database.getDataFromTable();
            users.forEach(user -> {
                if (!user.getUser().equals(file.getOwnerUser())) {
                    userComboBox.getItems().add(user.getUser());
                }
            });
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        
        CheckBox readPermissionBox = new CheckBox("Can read");
        CheckBox writePermissionBox = new CheckBox("Can edit");
        
        grid.add(new Label("Share with:"), 0, 0);
        grid.add(userComboBox, 1, 0);
        grid.add(readPermissionBox, 0, 1);
        grid.add(writePermissionBox, 0, 2);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Handle the result
        dialog.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String selectedUser = userComboBox.getValue();
                if (selectedUser != null) {
                    try {
                        database.setFilePermissions(
                                file.getFileId(),
                                selectedUser,
                                readPermissionBox.isSelected(),
                                writePermissionBox.isSelected(),
                                currentUser  // current user is granting the permissions
                        );
                        
                        showAlert(Alert.AlertType.INFORMATION, "Success",
                                "File shared successfully");
                        
                        refreshFilesList();
                        
                    } catch (ClassNotFoundException e) {
                        showAlert(Alert.AlertType.ERROR, "Error",
                                "Failed to update permissions: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    private void openTextEditor(FileMetadata file) {
        try {
            // Check read permission first
            if (!database.checkFilePermission(file.getFileId(), currentUser, "read")) {
                showAlert(Alert.AlertType.ERROR, "Access Denied",
                        "You don't have permission to view this file");
                return;
            }
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("text_editor.fxml"));
            Parent root = loader.load();
            
            TextEditorController controller = loader.getController();
            controller.setCurrentUser(currentUser);
            controller.loadFile(file);
            
            Stage editorStage = new Stage();
            editorStage.setTitle("Edit: " + file.getFileName());
            editorStage.setScene(new Scene(root, 800, 600));
            editorStage.show();
            
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to open text editor: " + e.getMessage());
        }
    }
    
// Add a button to create new text files
    @FXML
    private void createNewTextFile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("text_editor.fxml"));
            Parent root = loader.load();
            
            TextEditorController controller = loader.getController();
            controller.setCurrentUser(currentUser);
            
            Stage editorStage = new Stage();
            editorStage.setTitle("New Text File");
            editorStage.setScene(new Scene(root, 800, 600));
            editorStage.show();
            
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to open text editor: " + e.getMessage());
        }
    }
    
    @FXML
    private void openTerminal() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("terminal.fxml"));
            Parent root = loader.load();
            
            Stage terminalStage = new Stage();
            terminalStage.setTitle("Terminal");
            terminalStage.setScene(new Scene(root, 600, 400));
            
            // Make terminal window stay on top
            terminalStage.setAlwaysOnTop(true);
            
            terminalStage.show();
            
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to open terminal: " + e.getMessage());
        }
    }
    
    /**
     * Shows who has access to a file
     */
    private void showAccessList(FileMetadata file) {
        try {
            List<Map<String, Object>> accessList = database.getFileAccessList(file.getFileId());
            
            // Create dialog to show access information
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Access Information");
            dialog.setHeaderText("Who has access to " + file.getFileName());
            
            // Create a VBox to hold the access information
            VBox content = new VBox(10);
            content.setPadding(new Insets(10));
            
            // Add owner information first
            Label ownerLabel = new Label("Owner: " + file.getOwnerUser() + " (full access)");
            ownerLabel.setStyle("-fx-font-weight: bold");
            content.getChildren().add(ownerLabel);
            
            // Add separator
            content.getChildren().add(new Separator());
            
            // Add shared access information
            for (Map<String, Object> access : accessList) {
                if (!access.get("userName").equals(file.getOwnerUser())) {
                    VBox userBox = new VBox(5);
                    userBox.getChildren().addAll(
                            new Label("User: " + access.get("userName")),
                            new Label("Can read: " + access.get("canRead")),
                            new Label("Can write: " + access.get("canWrite")),
                            new Label("Granted by: " + access.get("grantedBy")),
                            new Label("Granted on: " + access.get("dateGranted")),
                            new Separator()
                    );
                    content.getChildren().add(userBox);
                }
            }
            
            // Add scrolling if there are many users
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefHeight(300);
            
            dialog.getDialogPane().setContent(scrollPane);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            
            dialog.showAndWait();
            
        } catch (ClassNotFoundException e) {
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Failed to get access information: " + e.getMessage());
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
            System.out.println("Selected file for upload: " + selectedFile.getAbsolutePath());
            fileTextField.setText(selectedFile.getAbsolutePath());
            String fileId = UUID.randomUUID().toString();
            
            setControlsEnabled(false);
            operationCancelled = false;
            
            Task<Void> uploadTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    try {
                        System.out.println("Starting upload process for fileId: " + fileId);
                        uploadFileInChunks(selectedFile, fileId);
                        return null;
                    } catch (Exception e) {
                        System.err.println("Upload failed: " + e.getMessage());
                        e.printStackTrace();
                        throw e;
                    }
                }
            };
            
            uploadTask.setOnSucceeded(e -> {
                System.out.println("Upload completed successfully");
                setControlsEnabled(true);
                clearProgress();
                refreshFilesList();
                showAlert(Alert.AlertType.INFORMATION, "Success", "File uploaded successfully");
            });
            
            uploadTask.setOnFailed(e -> {
                System.err.println("Upload task failed");
                Throwable exc = uploadTask.getException();
                if (exc != null) {
                    System.err.println("Error details: " + exc.getMessage());
                    exc.printStackTrace();
                }
                setControlsEnabled(true);
                clearProgress();
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
        try {
            // Check download permission
            if (!database.checkFilePermission(metadata.getFileId(), currentUser, "read")) {
                showAlert(Alert.AlertType.ERROR, "Access Denied",
                        "You don't have permission to download this file");
                return;
            }
            
            // Setup file chooser and get save location
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
        } catch (ClassNotFoundException e) {
            showAlert(Alert.AlertType.ERROR, "System Error",
                    "Failed to verify file permissions: " + e.getMessage());
        }
    }
    
    /**
     * Uploads a file in chunks using the load balancer
     */
    public void uploadFileInChunks(File file, String fileId) throws IOException {
        FileLockManager lockManager = FileLockManager.getInstance();
        
        // attempt to acquire lock for upload
        if (!lockManager.lockFile(fileId, "UPLOAD", currentUser)) {
            throw new IOException("File is locked by another operation. Please try again later.");
        }
        
        try {
            System.out.println("\nDebug: Starting upload process");
            System.out.println("File: " + file.getName());
            System.out.println("File ID: " + fileId);
            
            FileChunker chunker = new FileChunker();
            int chunkSize = chunker.getOptimalChunkSize(file.length());
            System.out.println("Optimal chunk size: " + chunkSize + " bytes");
            
            List<FileChunker.ChunkInfo> chunks = chunker.splitFile(file, chunkSize);
            System.out.println("Total chunks created: " + chunks.size());
            
            // Create metadata at start of upload
            FileMetadata metadata = new FileMetadata(fileId, file.getName(), currentUser, file.length());
            metadata.setTotalChunks(chunks.size());
            
            long totalSize = file.length();
            long bytesProcessed = 0;
            
            // First, process all chunks without delay
            for (FileChunker.ChunkInfo chunk : chunks) {
                System.out.println("\nProcessing chunk " + chunk.getNumber());
                if (operationCancelled) {
                    System.out.println("Upload cancelled by user");
                    break;
                }
                
                try {
                    // Update progress for chunk processing
                    final long finalBytesProcessed = bytesProcessed;
                    Platform.runLater(() -> updateProgress((double)finalBytesProcessed / totalSize * 0.5));
                    
                    // Upload the encrypted chunk data
                    String containerId = loadBalancerClient.uploadFileChunk(fileId, chunk.getNumber(), chunk.getData());
                    
                    if (containerId != null) {
                        System.out.println("Chunk " + chunk.getNumber() + " stored in container: " + containerId);
                        metadata.addChunkLocation(chunk.getNumber(), containerId);
                        
                        // Store the encryption key that was used to encrypt this chunk
                        // Only store key after successful chunk upload
                        database.storeEncryptionKey(fileId, chunk.getNumber(), chunk.getEncryptionKey());
                        System.out.println("Encryption key stored for chunk " + chunk.getNumber());
                        
                        bytesProcessed += chunk.getSize();
                    }
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(FileOperationsController.class.getName()).log(Level.SEVERE, null, ex);
                    throw new IOException("Upload failed", ex);
                }
            }
            
            // After all chunks are processed, simulate network delay based on file size
            if (!operationCancelled) {
                try {
                    // Determine traffic level based on file size
                    if (totalSize > 10 * 1024 * 1024) { // 10MB
                        NetworkSimulator.setTrafficLevel(NetworkSimulator.TrafficLevel.HIGH);
                    } else if (totalSize > 5 * 1024 * 1024) { // 5MB
                        NetworkSimulator.setTrafficLevel(NetworkSimulator.TrafficLevel.MEDIUM);
                    } else {
                        NetworkSimulator.setTrafficLevel(NetworkSimulator.TrafficLevel.LOW);
                    }
                    
                    // Simulate network delay for the entire file
                    System.out.println("Simulating network delay for complete file transfer...");
                    NetworkSimulator.simulateNetworkDelayWithProgress(
                            "Completing file transfer",
                            progress -> Platform.runLater(() -> updateProgress(0.5 + progress * 0.5))
                    );
                    
                    // Save metadata after successful transfer
                    database.saveFileMetadata(metadata);
                    System.out.println("Successfully saved metadata for file: " + metadata.getFileName());
                    
                } catch (InterruptedException | ClassNotFoundException ex) {
                    Logger.getLogger(FileOperationsController.class.getName()).log(Level.SEVERE, null, ex);
                    throw new IOException("Upload failed during network simulation", ex);
                }
            }
        } finally {
            lockManager.unlockFile(fileId, currentUser);
        }
    }
    
    // Helper method to show alerts on the JavaFX Application Thread
    protected void showAlert(Alert.AlertType type, String title, String message) {
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
    public void downloadAndAssembleFile(String fileId, File outputFile)
            throws IOException, ClassNotFoundException {
        
        FileLockManager lockManager = FileLockManager.getInstance();
        
        try {
            if (!lockManager.lockFile(fileId, "DOWNLOAD", currentUser)) {
                throw new IOException("File is locked by another operation. Please try again later.");
            }
            
            System.out.println("Starting download for file ID: " + fileId + "...");
            
            FileMetadata metadata = database.getFileMetadata(fileId);
            if (metadata == null) {
                throw new IOException("File metadata not found");
            }
            
            // Set traffic level based on file size
            if (metadata.getTotalSize() > 10 * 1024 * 1024) {
                NetworkSimulator.setTrafficLevel(NetworkSimulator.TrafficLevel.HIGH);
            } else if (metadata.getTotalSize() > 5 * 1024 * 1024) {
                NetworkSimulator.setTrafficLevel(NetworkSimulator.TrafficLevel.MEDIUM);
            } else {
                NetworkSimulator.setTrafficLevel(NetworkSimulator.TrafficLevel.LOW);
            }
            
            List<FileChunker.ChunkInfo> chunks = new ArrayList<>();
            long bytesProcessed = 0;
            
            // Initialize checksum
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new IOException("Failed to initialize checksum", e);
            }
            
            // First phase (0-50%): Download and verify all chunks
            for (int chunkNumber = 0; chunkNumber < metadata.getTotalChunks(); chunkNumber++) {
                if (operationCancelled) break;
                
                System.out.println("Requesting chunk " + chunkNumber + " from load balancer...");
                
                try {
                    // Download encrypted chunk
                    byte[] encryptedData = loadBalancerClient.downloadFileChunk(fileId, chunkNumber);
                    System.out.println("Received chunk " + chunkNumber + ", size: " + encryptedData.length);
                    
                    // Calculate checksum of encrypted data
                    md.reset();
                    md.update(encryptedData);
                    String checksum = Base64.getEncoder().encodeToString(md.digest());
                    
                    // Get encryption key
                    String encryptionKey = database.getEncryptionKey(fileId, chunkNumber);
                    if (encryptionKey == null) {
                        throw new IOException("Missing encryption key for chunk " + chunkNumber);
                    }
                    System.out.println("Retrieved encryption key for chunk " + chunkNumber);
                    
                    // Create chunk info
                    FileChunker.ChunkInfo chunk = new FileChunker.ChunkInfo(
                            chunkNumber,
                            encryptedData.length,
                            checksum,
                            encryptionKey,
                            encryptedData
                    );
                    
                    chunks.add(chunk);
                    bytesProcessed += encryptedData.length;
                    
                    // Update progress (0-50% range)
                    final double progress = (double) bytesProcessed / metadata.getTotalSize() * 0.5;
                    Platform.runLater(() -> updateProgress(progress));
                    
                } catch (Exception e) {
                    throw new IOException("Failed to download chunk " + chunkNumber + ": " + e.getMessage(), e);
                }
            }
            
            // Second phase (50-100%): Network delay simulation and file assembly
            if (!operationCancelled) {
                try {
                    System.out.println("Simulating network delay for file assembly...");
                    NetworkSimulator.simulateNetworkDelayWithProgress(
                            "Assembling file chunks",
                            progress -> Platform.runLater(() -> updateProgress(0.5 + progress * 0.5))
                    );
                    
                    // Reassemble file using chunks
                    FileChunker chunker = new FileChunker();
                    chunker.reassembleFile(chunks, outputFile);
                    
                    System.out.println("File download and assembly completed successfully");
                    
                } catch (InterruptedException e) {
                    throw new IOException("Download interrupted during network simulation", e);
                }
            }
            
        } finally {
            lockManager.unlockFile(fileId, currentUser);
            System.out.println("Released lock for file: " + fileId);
        }
    }
    
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
    protected void updateProgress(double progress) {
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
    protected void clearProgress() {
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
            FileLockManager lockManager = FileLockManager.getInstance();
            
            try {
                // First, attempt to acquire a lock on the file to prevent concurrent access
                if (!lockManager.lockFile(file.getFileId(), "DELETE", currentUser)) {
                    Platform.runLater(() -> {
                        setControlsEnabled(true);
                        showAlert(Alert.AlertType.WARNING, "File Locked",
                                "File is currently in use by another operation. Please try again later.");
                    });
                    return;
                }
                
                // Delete chunks from containers with progress tracking
                int totalChunks = file.getTotalChunks();
                int chunksProcessed = 0;
                
                // Iterate through each chunk location and delete from containers
                for (int i = 0; i < totalChunks; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Delete operation cancelled");
                    }
                    
                    String containerId = file.getContainerForChunk(i);
                    if (containerId != null) {
                        System.out.println("Deleting chunk " + i + " from container: " + containerId);
                        
                        try {
                            // Delete the chunk from the container
                            FileStorageContainer container = getContainerById(containerId);
                            if (container != null) {
                                String chunkPath = "/storage/" + containerId + "/" + file.getFileId() + "_chunk_" + i;
                                container.deleteFileChunk(chunkPath);
                            }
                            
                            // Update progress as each chunk is deleted
                            chunksProcessed++;
                            final double progress = (double) chunksProcessed / totalChunks;
                            Platform.runLater(() -> updateProgress(progress));
                            
                        } catch (Exception e) {
                            throw new IOException("Failed to delete chunk " + i +
                                    " from container " + containerId, e);
                        }
                    }
                }
                
                // After all chunks are deleted, remove the metadata from database
                database.deleteFileMetadata(file.getFileId());
                
                // Update UI with success message
                Platform.runLater(() -> {
                    refreshFilesList();
                    setControlsEnabled(true);
                    updateProgress(1.0);
                    showAlert(Alert.AlertType.INFORMATION, "Success",
                            "File deleted successfully");
                });
                
            } catch (Exception e) {
                final String errorMessage = e.getMessage();
                Platform.runLater(() -> {
                    setControlsEnabled(true);
                    showAlert(Alert.AlertType.ERROR, "Delete Error",
                            "Failed to delete file: " + errorMessage);
                });
                
            } finally {
                // Always release the lock, even if deletion failed
                lockManager.unlockFile(file.getFileId(), currentUser);
                System.out.println("Released lock for file: " + file.getFileId());
            }
        }).start();
    }
    
// Helper method to find container by ID
    private FileStorageContainer getContainerById(String containerId) {
        // This method would need to be implemented to retrieve the appropriate
        // container instance based on the ID
        // You might want to maintain a map of containers or retrieve them from
        // your load balancer
        return null; // Implement actual container lookup logic
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
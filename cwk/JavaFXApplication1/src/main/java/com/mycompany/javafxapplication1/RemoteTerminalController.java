package com.mycompany.javafxapplication1;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.application.Platform;
import com.jcraft.jsch.JSchException;
import java.io.IOException;

public class RemoteTerminalController {
    @FXML private ComboBox<String> containerComboBox;
    @FXML private Button connectButton;
    @FXML private Label statusLabel;
    @FXML private TextArea outputArea;
    @FXML private TextField commandInput;
    
    private ContainerSSH ssh;
    private StringBuilder history;
    
    @FXML
    public void initialize() {
        ssh = new ContainerSSH();
        history = new StringBuilder();
        
        // Initialize container list
        containerComboBox.getItems().addAll(
                "container-1", "container-2",
                "container-3", "container-4"
        );
        
        // Handle command input
        commandInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                executeCommand(commandInput.getText());
                commandInput.clear();
            }
        });
        
        // Show initial message
        appendOutput("Remote Terminal Ready\nSelect a container and click Connect\n");
    }
    
    @FXML
    private void handleConnect() {
        String containerId = containerComboBox.getValue();
        if (containerId == null) {
            showError("Please select a container");
            return;
        }
        
        try {
            if (!ssh.isConnected()) {
                // In a real application, you'd get these from a secure configuration
                ssh.connect(containerId, "ntu-user", "ntu-user");
                updateConnectionStatus(true);
                appendOutput("Connected to " + containerId + "\n");
            } else {
                ssh.disconnect();
                updateConnectionStatus(false);
                appendOutput("Disconnected from " + containerId + "\n");
            }
        } catch (JSchException e) {
            showError("Connection failed: " + e.getMessage());
            updateConnectionStatus(false);
        }
    }
    
    private void executeCommand(String command) {
        if (!ssh.isConnected()) {
            showError("Not connected to container");
            return;
        }
        
        try {
            appendOutput("> " + command + "\n");
            String response = ssh.executeCommand(command);
            appendOutput(response);
        } catch (IOException e) {
            showError("Command failed: " + e.getMessage());
        }
    }
    
    @FXML
    private void showSystemInfo() {
        if (!ssh.isConnected()) {
            showError("Not connected to container");
            return;
        }
        
        try {
            String info = ssh.getSystemInfo();
            appendOutput(info);
        } catch (IOException e) {
            showError("Failed to get system info: " + e.getMessage());
        }
    }
    
    @FXML
    private void listFiles() {
        executeCommand("ls -la");
    }
    
    @FXML
    private void showProcesses() {
        executeCommand("ps aux");
    }
    
    @FXML
    private void showDiskUsage() {
        executeCommand("df -h");
    }
    
    private void updateConnectionStatus(boolean connected) {
        Platform.runLater(() -> {
            statusLabel.setText(connected ? "Connected" : "Not Connected");
            statusLabel.setStyle(connected ?"-fx-text-fill: green;" : "-fx-text-fill: red;");
            connectButton.setText(connected ? "Disconnect" : "Connect");
            commandInput.setDisable(!connected);
        });
    }
    
    private void appendOutput(String text) {
        Platform.runLater(() -> {
            history.append(text);
            outputArea.setText(history.toString());
            outputArea.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
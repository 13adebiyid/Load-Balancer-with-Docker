package com.mycompany.javafxapplication1;

import com.mycompany.javafxapplication1.LoadBalancer;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
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
    private void uploadBtnHandler(ActionEvent event) {
        Stage primaryStage = (Stage) uploadBtn.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedFile != null) {
            fileTextField.setText(selectedFile.getAbsolutePath());
            System.out.println("File selected for upload: " + selectedFile.getAbsolutePath());

            // Send the file path to the load balancer
            LoadBalancer loadBalancer = new LoadBalancer();
            String server = loadBalancer.handleFileUpload(selectedFile.getAbsolutePath(), "RR");  // Use Round Robin
            dialogue("File Upload", "File uploaded to server: " + server);
        }
    }


        @FXML
        private void downloadBtnHandler(ActionEvent event) {
        Stage primaryStage = (Stage) downloadBtn.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        File selectedFile = fileChooser.showSaveDialog(primaryStage);

        if (selectedFile != null) {
            fileTextField.setText(selectedFile.getAbsolutePath());
            System.out.println("File selected for download: " + selectedFile.getAbsolutePath());

            // Send the file path to the load balancer
            LoadBalancer loadBalancer = new LoadBalancer();
            String server = loadBalancer.handleFileDownload(selectedFile.getAbsolutePath(), "RR");  // Use Round Robin
            dialogue("File Download", "File downloaded from server: " + server);
        }
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
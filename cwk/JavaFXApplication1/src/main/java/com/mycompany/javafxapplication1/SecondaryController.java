package com.mycompany.javafxapplication1;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;



public class SecondaryController {
    
    @FXML private TextField userTextField;   
    @FXML private TableView dataTableView;    
    @FXML private Button secondaryButton;   
    @FXML private Button refreshBtn;   
    @FXML private TextField customTextField;    
    @FXML private Button fileOperationsBtn;
    
    private String currentUserRole;
    private DB myObj;
    
    @FXML 
    private void switchToFileOperations() {
        Stage fileOperationsStage = new Stage();
        Stage currentStage = (Stage) fileOperationsBtn.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("file_operations.fxml"));
            Parent root = loader.load();
            
            // Get controller, set current user
            FileOperationsController controller = loader.getController();
            controller.setCurrentUser(userTextField.getText());
            
            Scene scene = new Scene(root, 640, 480);
            fileOperationsStage.setScene(scene);
            fileOperationsStage.setTitle("File Operations");
            fileOperationsStage.show();
            currentStage.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    @FXML
    private void RefreshBtnHandler(ActionEvent event){
        Stage primaryStage = (Stage) customTextField.getScene().getWindow();
        customTextField.setText((String)primaryStage.getUserData());
        refreshUserTable();
    }
    
    @FXML
    private void switchToPrimary(){
        Stage secondaryStage = new Stage();
        Stage primaryStage = (Stage) secondaryButton.getScene().getWindow();
        try {
            
            
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("primary.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Login");
            secondaryStage.show();
            primaryStage.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void initialise(String[] credentials) {
        userTextField.setText(credentials[0]);
        myObj = new DB();
        
        try {
            // Get current user's role
            currentUserRole = myObj.getUserRole(credentials[0]);
            
            // Setup table columns
            TableColumn<User, String> userCol = new TableColumn<>("Username");
            userCol.setCellValueFactory(new PropertyValueFactory<>("user"));
            
            TableColumn<User, String> roleCol = new TableColumn<>("Role");
            roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
            
            // Add context menu for admin actions
            if ("ADMIN".equals(currentUserRole)) {
                setupAdminContextMenu();
            }
            
            dataTableView.getColumns().addAll(userCol, roleCol);
            refreshUserTable();
            
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SecondaryController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void setupAdminContextMenu() {
        dataTableView.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem deleteItem = new MenuItem("Delete User");
            deleteItem.setOnAction(event -> {
                User user = row.getItem();
                if (user != null && !user.getUser().equals(userTextField.getText())) {
                    deleteUser(user);
                }
            });
            
            MenuItem editItem = new MenuItem("Edit User");
            editItem.setOnAction(event -> {
                User user = row.getItem();
                if (user != null) {
                    showEditDialog(user);
                }
            });
            
            MenuItem resetPasswordItem = new MenuItem("Reset Password");
            resetPasswordItem.setOnAction(event -> {
                User user = row.getItem();
                if (user != null) {
                    showResetPasswordDialog(user);
                }
            });
            
            contextMenu.getItems().addAll(editItem, resetPasswordItem, deleteItem);
            
            // Only show context menu for non-admin users
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu)
            );
            
            return row;
        });
    }
    
    private void showEditDialog(User user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User");
        dialog.setHeaderText("Edit user details for: " + user.getUser());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField usernameField = new TextField(user.getUser());
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("STANDARD", "ADMIN");
        roleCombo.setValue(user.getRole());
        
        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Role:"), 0, 1);
        grid.add(roleCombo, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Update user in database
                myObj.updateUser(user.getUser(), usernameField.getText(), roleCombo.getValue());
                refreshUserTable();
            } catch (Exception e) {
                showError("Failed to update user", e.getMessage());
            }
        }
    }
    
    private void showResetPasswordDialog(User user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Reset password for: " + user.getUser());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        PasswordField newPassField = new PasswordField();
        PasswordField confirmPassField = new PasswordField();
        
        grid.add(new Label("New Password:"), 0, 0);
        grid.add(newPassField, 1, 0);
        grid.add(new Label("Confirm Password:"), 0, 1);
        grid.add(confirmPassField, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (!newPassField.getText().equals(confirmPassField.getText())) {
                showError("Password Mismatch", "Passwords do not match");
                return;
            }
            
            try {
                myObj.resetPassword(user.getUser(), newPassField.getText());
                showInfo("Success", "Password has been reset");
            } catch (Exception e) {
                showError("Failed to reset password", e.getMessage());
            }
        }
    }
    
    private void deleteUser(User user) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete User");
        alert.setContentText("Are you sure you want to delete user: " + user.getUser() + "?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                myObj.deleteUser(user.getUser());
                refreshUserTable();
                showInfo("Success", "User deleted successfully");
            } catch (Exception e) {
                showError("Delete Failed", "Failed to delete user: " + e.getMessage());
            }
        }
    }
    
    private void refreshUserTable() {
        try {
            ObservableList<User> users = myObj.getDataFromTable();
            dataTableView.setItems(users);
            dataTableView.refresh();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SecondaryController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

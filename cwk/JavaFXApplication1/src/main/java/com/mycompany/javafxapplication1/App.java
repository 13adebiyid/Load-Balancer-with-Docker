package com.mycompany.javafxapplication1;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX App
 */
public class App extends Application {
    
    @Override
    public void start(Stage stage) throws IOException {
        Stage secondaryStage = new Stage();
        DB myObj = new DB();
        myObj.log("-------- Initializing database connection ------------");
        
        try {
        // Initialize database - only creates tables if they don't exist
        myObj.initializeDatabase();
        
        myObj.log("Database initialization completed successfully");
        
    } catch (ClassNotFoundException ex) {
        Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        throw new RuntimeException("Failed to initialize database", ex);
    }

        try {
            myObj.createTables();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // Initialize and start the load balancer server
        LoadBalancer loadBalancer = new LoadBalancer();
        
        // Add storage containers
        for (int i = 1; i <= 4; i++) {
            FileStorageContainer container = new FileStorageContainer(
                    "container-" + i,
                    "/storage/container" + i
            );
            loadBalancer.addContainer(container);
        }
        
        // Start the server
        LoadBalancerServer server = new LoadBalancerServer(8080, loadBalancer);
        server.start();
        
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("primary.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Primary View");
            secondaryStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        launch();
    }
    
}
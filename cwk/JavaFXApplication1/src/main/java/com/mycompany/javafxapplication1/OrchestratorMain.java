/*
* Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
* Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
*/
package com.mycompany.javafxapplication1;

/**
 *
 * @author ntu-user
 */


/**
 * Main class for starting the Container Orchestrator service
 * This runs inside the GUI container and handles communication
 * with the Python orchestrator on the host machine
 */
public class OrchestratorMain {
    public static void main(String[] args) {
        try {
            ContainerOrchestrator orchestrator = new ContainerOrchestrator();
            System.out.println("Starting Container Orchestrator...");
            
            // Start the orchestrator in a separate thread since it has a blocking loop
            Thread orchestratorThread = new Thread(() -> {
                try {
                    orchestrator.start();
                } catch (Exception e) {
                    System.err.println("Error in orchestrator thread: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            // Set as daemon thread so it doesn't prevent JVM shutdown
            orchestratorThread.setDaemon(true);
            orchestratorThread.start();
            
            // Add shutdown hook to clean up
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Stopping Container Orchestrator...");
                orchestrator.stop();
            }));
            
            System.out.println("Container Orchestrator is running");
            
        } catch (Exception e) {
            System.err.println("Failed to start orchestrator: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
package com.mycompany.javafxapplication1;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */


import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Random;

public class LoadBalancer {
    private List<String> servers;
    private int currentIndex = 0;
    private Random random = new Random();

    public LoadBalancer(List<String> servers) {
        this.servers = servers;
    }

    // Round Robin algorithm
    public String getNextServerRoundRobin() {
        String server = servers.get(currentIndex);
        currentIndex = (currentIndex + 1) % servers.size();
        return server;
    }

    // Random algorithm
    public String getNextServerRandom() {
        return servers.get(random.nextInt(servers.size()));
    }

    // Send a chunk to a server (example using SSH/Socket)
    public void sendChunk(byte[] chunk, String server) throws IOException {
        try (Socket socket = new Socket(server, 22); // Use port 22 for SSH
             OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write(chunk);
            System.out.println("Chunk sent to server: " + server);
        }
    }

    public static void main(String[] args) {
        // List of file storage servers (matches docker-compose service names)
        List<String> servers = List.of(
            "comp20081-files-container1",
            "comp20081-files-container2",
            "comp20081-files-container3",
            "comp20081-files-container4"
        );

        LoadBalancer loadBalancer = new LoadBalancer(servers);

        // Example: Distribute a test chunk
        try {
            byte[] chunk = "Test chunk".getBytes();
            String targetServer = loadBalancer.getNextServerRoundRobin();
            loadBalancer.sendChunk(chunk, targetServer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

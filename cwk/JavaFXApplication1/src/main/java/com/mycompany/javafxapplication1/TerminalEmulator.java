package com.mycompany.javafxapplication1;

import java.io.*;
import java.util.*;

public class TerminalEmulator {
    private String currentDirectory;
    private final Map<String, CommandExecutor> commands;

    public TerminalEmulator() {
        currentDirectory = System.getProperty("user.home");
        commands = new HashMap<>();
        initializeCommands();
    }

    private void initializeCommands() {
        commands.put("ls", this::listFiles);
        commands.put("cd", this::changeDirectory);
        commands.put("pwd", this::printWorkingDirectory);
        commands.put("mkdir", this::makeDirectory);
        commands.put("cp", this::copyFile);
        commands.put("mv", this::moveFile);
        commands.put("whoami", this::whoami);
        commands.put("tree", this::treeCommand);
    }

    public String executeCommand(String input) {
        String[] parts = input.trim().split("\\s+");
        String command = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        CommandExecutor executor = commands.get(command);
        if (executor != null) {
            return executor.execute(args);
        }
        return "Command not found: " + command;
    }

    private String listFiles(String[] args) {
        File dir = new File(currentDirectory);
        String[] files = dir.list();
        if (files == null) return "Cannot access directory";
        return String.join("\n", files);
    }

    private String changeDirectory(String[] args) {
        if (args.length < 1) return "Usage: cd <directory>";
        
        String newPath = args[0];
        File newDir = new File(newPath.startsWith("/") ? newPath : currentDirectory + "/" + newPath);
        
        if (newDir.exists() && newDir.isDirectory()) {
            try {
                currentDirectory = newDir.getCanonicalPath();
                return "";
            } catch (IOException e) {
                return "Error: " + e.getMessage();
            }
        }
        return "Directory not found: " + newPath;
    }

    // Implement other command methods...

    @FunctionalInterface
    private interface CommandExecutor {
        String execute(String[] args);
    }
}
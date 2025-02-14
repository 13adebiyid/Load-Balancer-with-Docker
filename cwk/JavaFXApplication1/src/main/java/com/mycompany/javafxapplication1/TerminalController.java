package com.mycompany.javafxapplication1;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class TerminalController {
    @FXML private TextArea outputArea;
    @FXML private TextField commandInput;
    
    private TerminalEmulator terminal;
    private StringBuilder history;
    
    @FXML
    public void initialize() {
        terminal = new TerminalEmulator();
        history = new StringBuilder();
        
        appendOutput("Terminal Ready\n");
        
        commandInput.setOnKeyPressed(this::handleCommandInput);
    }
    
    private void handleCommandInput(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String command = commandInput.getText();
            
            appendOutput("> " + command + "\n");
            
            if (!command.trim().isEmpty()) {
                String result = terminal.executeCommand(command);
                if (!result.isEmpty()) {
                    appendOutput(result + "\n");
                }
            }
            
            commandInput.clear();
        }
    }
    
    private void appendOutput(String text) {
        history.append(text);
        outputArea.setText(history.toString());
        outputArea.setScrollTop(Double.MAX_VALUE); 
    }
}
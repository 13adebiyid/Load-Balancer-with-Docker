package com.mycompany.javafxapplication1;

import javafx.beans.property.SimpleStringProperty;

/**
 * Represents a user in the system with their credentials and role
 */
public class User {
    private SimpleStringProperty user;
    private SimpleStringProperty pass;
    private SimpleStringProperty role;
    
    /**
     * Defines possible user roles in the system
     */
    public enum UserRole {
        STANDARD,
        ADMIN
    }
    
    /**
     * Creates a new user with the specified credentials and default STANDARD role
     */
    User(String user, String pass) {
        this(user, pass, UserRole.STANDARD.toString());
    }
    
    /**
     * Creates a new user with the specified credentials and role
     */
    User(String user, String pass, String role) {
        this.user = new SimpleStringProperty(user);
        this.pass = new SimpleStringProperty(pass);
        this.role = new SimpleStringProperty(role != null ? role : UserRole.STANDARD.toString());
    }
    
    public String getUser() {
        return user.get();
    }
    
    public void setUser(String user) {
        this.user.set(user);
    }
    
    public String getPass() {
        return pass.get();
    }
    
    public void setPass(String pass) {
        this.pass.set(pass);
    }
    
    public String getRole() {
        return role.get();
    }
    
    public void setRole(String role) {
        this.role.set(role);
    }
    
    /**
     * Checks if the user has admin privileges
     */
    public boolean isAdmin() {
        return UserRole.ADMIN.toString().equals(getRole());
    }
}
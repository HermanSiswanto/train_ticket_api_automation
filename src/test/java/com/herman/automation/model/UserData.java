package com.herman.automation.model;

public final class UserData {

    private final String name;
    private final String email;
    private final String role;

    public UserData(String name, String email, String role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
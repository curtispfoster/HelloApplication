package com.example.helloapplication;

public enum Role {
    USER, ADMIN, OWNER;

    public static Role fromString(String roleName) {
        if (roleName == null) {
            return null;
        }
        try {
            return Role.valueOf(roleName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

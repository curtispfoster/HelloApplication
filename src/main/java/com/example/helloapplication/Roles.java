package com.example.helloapplication;

public class Roles {
    public final Authenticator.Status status;
    public final String username;
    public final String roleName;
    public final Role role;

    public Roles(Authenticator.Status status, String username, String roleName){
        this.status = status;
        this.username = username;
        this.roleName = roleName;
        this.role = Role.fromString(roleName);
    }

    public boolean isAdmin() {
        return status == Authenticator.Status.OK
                && (role == Role.ADMIN || role == Role.OWNER);
    }

    public boolean isOwner() {
        return status == Authenticator.Status.OK && role == Role.OWNER;
    }

}

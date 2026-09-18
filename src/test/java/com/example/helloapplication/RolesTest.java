package com.example.helloapplication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolesTest {

    @Test
    void adminWhenOkAndRoleAdmin() {
        Roles roles = new Roles(Authenticator.Status.OK, "admin", "ADMIN");

        assertTrue(roles.isAdmin());
        assertEquals("admin", roles.username);
        assertEquals("ADMIN", roles.roleName);
    }

    @Test
    void adminIgnoresRoleCase() {
        Roles roles = new Roles(Authenticator.Status.OK, "admin", "admin");

        assertTrue(roles.isAdmin());
    }

    @Test
    void notAdminWhenRoleIsUser() {
        Roles roles = new Roles(Authenticator.Status.OK, "bob", "USER");

        assertFalse(roles.isAdmin());
    }

    @Test
    void notAdminWhenStatusWrong() {
        Roles roles = new Roles(Authenticator.Status.WRONG, "admin", "ADMIN");

        assertFalse(roles.isAdmin());
    }

    @Test
    void notAdminWhenStatusEmpty() {
        Roles roles = new Roles(Authenticator.Status.EMPTY, null, null);

        assertFalse(roles.isAdmin());
    }

    @Test
    void notAdminWhenRoleNull() {
        Roles roles = new Roles(Authenticator.Status.OK, "admin", null);

        assertFalse(roles.isAdmin());
    }

    @Test
    void ownerIsAlsoAdmin() {
        Roles roles = new Roles(Authenticator.Status.OK, "root", "OWNER");

        assertTrue(roles.isOwner());
        assertTrue(roles.isAdmin());
    }

    @Test
    void adminIsNotOwner() {
        Roles roles = new Roles(Authenticator.Status.OK, "admin", "ADMIN");

        assertFalse(roles.isOwner());
    }

    @Test
    void mustChangePasswordDefaultsFalse() {
        Roles roles = new Roles(Authenticator.Status.OK, "admin", "ADMIN");

        assertFalse(roles.mustChangePassword);
    }

    @Test
    void mustChangePasswordCanBeSetTrue() {
        Roles roles = new Roles(Authenticator.Status.OK, "admin", "ADMIN", true);

        assertTrue(roles.mustChangePassword);
    }
}

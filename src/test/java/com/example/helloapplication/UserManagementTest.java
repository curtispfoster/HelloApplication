package com.example.helloapplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

class UserManagementTest {

    @TempDir
    Path tempDir;

    private Database database;
    private UserManagement userManagement;

    private static final Roles OWNER = new Roles(Authenticator.Status.OK, "owner", "OWNER");
    private static final Roles ADMIN = new Roles(Authenticator.Status.OK, "admin", "ADMIN");
    private static final Roles USER = new Roles(Authenticator.Status.OK, "bob", "USER");

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("users.db"));
        database.init(); // seeds admin/ADMIN and owner/OWNER
        userManagement = new UserManagement(database);
    }

    @Test
    void adminCannotDeleteOwner() {
        UserManagement.Status result = userManagement.deleteUser(ADMIN, "owner");

        assertEquals(UserManagement.Status.FORBIDDEN, result);
        assertTrue(userExists("owner"));
    }

    @Test
    void ownerCanDeleteOwner() {
        UserManagement.Status result = userManagement.deleteUser(OWNER, "owner");

        assertEquals(UserManagement.Status.OK, result);
        assertFalse(userExists("owner"));
    }

    @Test
    void adminCanDeleteAdmin() {
        UserManagement.Status result = userManagement.deleteUser(ADMIN, "admin");

        assertEquals(UserManagement.Status.OK, result);
        assertFalse(userExists("admin"));
    }

    @Test
    void nonAdminCannotDeleteAnyone() {
        UserManagement.Status result = userManagement.deleteUser(USER, "admin");

        assertEquals(UserManagement.Status.FORBIDDEN, result);
        assertTrue(userExists("admin"));
    }

    @Test
    void deletingUnknownUserIsNotFound() {
        UserManagement.Status result = userManagement.deleteUser(OWNER, "nobody");

        assertEquals(UserManagement.Status.NOT_FOUND, result);
    }

    private boolean userExists(String username) {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM Users WHERE Username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

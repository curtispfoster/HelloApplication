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
        database.init();
        TestAccounts.seedAdminAndOwner(database); // admin/ADMIN and owner/OWNER
        userManagement = new UserManagement(database);
    }

    @Test
    void adminCannotDeleteOwner() {
        UserManagement.Status result = userManagement.deleteUser(ADMIN, "owner");

        assertEquals(UserManagement.Status.FORBIDDEN, result);
        assertTrue(userExists("owner"));
    }

    @Test
    void ownerCannotDeleteSelf() {
        UserManagement.Status result = userManagement.deleteUser(OWNER, "owner");

        assertEquals(UserManagement.Status.FORBIDDEN, result);
        assertTrue(userExists("owner"));
    }

    @Test
    void adminCannotDeleteSelf() {
        UserManagement.Status result = userManagement.deleteUser(ADMIN, "admin");

        assertEquals(UserManagement.Status.FORBIDDEN, result);
        assertTrue(userExists("admin"));
    }

    @Test
    void adminCanDeleteOtherAdmin() {
        insertUser("second-admin", "ADMIN");

        UserManagement.Status result = userManagement.deleteUser(ADMIN, "second-admin");

        assertEquals(UserManagement.Status.OK, result);
        assertFalse(userExists("second-admin"));
    }

    @Test
    void ownerCanDeleteOtherOwner() {
        insertUser("second-owner", "OWNER");

        UserManagement.Status result = userManagement.deleteUser(OWNER, "second-owner");

        assertEquals(UserManagement.Status.OK, result);
        assertFalse(userExists("second-owner"));
    }

    @Test
    void ownerCannotDeleteLastOwnerEvenIfNotSelf() {
        // "owner" is the only OWNER row; a different OWNER session must still
        // be blocked from deleting it down to zero OWNERs.
        Roles otherOwnerSession = new Roles(Authenticator.Status.OK, "some-other-owner", "OWNER");

        UserManagement.Status result = userManagement.deleteUser(otherOwnerSession, "owner");

        assertEquals(UserManagement.Status.FORBIDDEN, result);
        assertTrue(userExists("owner"));
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

    @Test
    void adminCannotResetOwnerPassword() {
        UserManagement.ResetResult result = userManagement.resetPassword(ADMIN, "owner");

        assertEquals(UserManagement.Status.FORBIDDEN, result.status());
        assertNull(result.temporaryPassword());
    }

    @Test
    void ownerCanResetAdminPassword() {
        UserManagement.ResetResult result = userManagement.resetPassword(OWNER, "admin");

        assertEquals(UserManagement.Status.OK, result.status());
        assertTrue(PasswordPolicy.meetsRequirements(result.temporaryPassword()));
        assertTrue(Argon2PasswordHasher.verify(result.temporaryPassword().toCharArray(), passwordHash("admin")));
        assertTrue(mustChangePassword("admin"));
    }

    @Test
    void adminCanResetOtherAdminPassword() {
        insertUser("second-admin", "ADMIN");

        UserManagement.ResetResult result = userManagement.resetPassword(ADMIN, "second-admin");

        assertEquals(UserManagement.Status.OK, result.status());
        assertTrue(mustChangePassword("second-admin"));
    }

    @Test
    void adminCanResetOwnPassword() {
        // Unlike deleteUser, resetting yourself isn't destructive, so it's allowed.
        UserManagement.ResetResult result = userManagement.resetPassword(ADMIN, "admin");

        assertEquals(UserManagement.Status.OK, result.status());
    }

    @Test
    void nonAdminCannotResetAnyone() {
        UserManagement.ResetResult result = userManagement.resetPassword(USER, "admin");

        assertEquals(UserManagement.Status.FORBIDDEN, result.status());
    }

    @Test
    void resettingUnknownUserIsNotFound() {
        UserManagement.ResetResult result = userManagement.resetPassword(OWNER, "nobody");

        assertEquals(UserManagement.Status.NOT_FOUND, result.status());
    }

    private void insertUser(String username, String role) {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO Users (Username, Password, Role) VALUES (?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, Argon2PasswordHasher.hash("irrelevant".toCharArray()));
            ps.setString(3, role);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private String passwordHash(String username) {
        return userColumn(username, "Password", ResultSet::getString);
    }

    private boolean mustChangePassword(String username) {
        return userColumn(username, "MustChangePassword", ResultSet::getInt) != 0;
    }

    private <T> T userColumn(String username, String column, ColumnReader<T> reader) {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + column + " FROM Users WHERE Username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), username + " not found");
                return reader.read(rs, column);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface ColumnReader<T> {
        T read(ResultSet rs, String column) throws Exception;
    }
}

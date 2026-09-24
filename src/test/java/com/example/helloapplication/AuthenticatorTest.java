package com.example.helloapplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatorTest {

    @TempDir
    Path tempDir;

    private Authenticator auth;

    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(tempDir.resolve("users.db"));
        database.init();
        TestAccounts.seedAdminAndOwner(database); // admin / secret / ADMIN
        auth = new Authenticator(database);
    }

    @Test
    void emptyWhenUserBlank() {
        Roles result = auth.checkLogin("  ", "secret");

        assertEquals(Authenticator.Status.EMPTY, result.status);
        assertFalse(result.isAdmin());
    }

    @Test
    void emptyWhenPasswordBlank() {
        Roles result = auth.checkLogin("admin", "");

        assertEquals(Authenticator.Status.EMPTY, result.status);
    }

    @Test
    void emptyWhenBothNull() {
        Roles result = auth.checkLogin(null, null);

        assertEquals(Authenticator.Status.EMPTY, result.status);
    }

    @Test
    void okForSeededAdmin() {
        Roles result = auth.checkLogin("admin", "secret");

        assertEquals(Authenticator.Status.OK, result.status);
        assertEquals("admin", result.username);
        assertEquals("ADMIN", result.roleName);
        assertTrue(result.isAdmin());
        assertTrue(result.mustChangePassword, "seeded admin should be flagged to change its password");
    }

    @Test
    void wrongPasswordForExistingUsername() {
        Roles result = auth.checkLogin("admin", "nope");

        assertEquals(Authenticator.Status.WRONG_PASSWORD, result.status);
        assertFalse(result.isAdmin());
    }

    @Test
    void wrongUsernameStaysGeneric() {
        Roles result = auth.checkLogin("nobody", "secret");

        assertEquals(Authenticator.Status.WRONG, result.status,
                "unknown username should never be distinguished from a generic failure");
    }

    @Test
    void trimsUsername() {
        Roles result = auth.checkLogin("  admin  ", "secret");

        assertEquals(Authenticator.Status.OK, result.status);
    }
}

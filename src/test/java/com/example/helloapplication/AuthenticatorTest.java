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
        database.init(); // seeds admin / secret / ADMIN
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
    }

    @Test
    void wrongPassword() {
        Roles result = auth.checkLogin("admin", "nope");

        assertEquals(Authenticator.Status.WRONG, result.status);
        assertFalse(result.isAdmin());
    }

    @Test
    void wrongUsername() {
        Roles result = auth.checkLogin("nobody", "secret");

        assertEquals(Authenticator.Status.WRONG, result.status);
    }

    @Test
    void trimsUsername() {
        Roles result = auth.checkLogin("  admin  ", "secret");

        assertEquals(Authenticator.Status.OK, result.status);
    }
}

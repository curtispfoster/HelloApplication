package com.example.helloapplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PasswordChangeTest {

    @TempDir
    Path tempDir;

    private Database database;
    private PasswordChange passwordChange;
    private Authenticator auth;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("users.db"));
        database.init(); // seeds admin/secret/ADMIN with MustChangePassword = 1
        passwordChange = new PasswordChange(database);
        auth = new Authenticator(database);
    }

    @Test
    void emptyWhenPasswordBlank() {
        PasswordChange.Status result = passwordChange.changePassword("admin", "");

        assertEquals(PasswordChange.Status.EMPTY, result);
    }

    @Test
    void weakWhenPasswordDoesNotMeetPolicy() {
        PasswordChange.Status result = passwordChange.changePassword("admin", "short");

        assertEquals(PasswordChange.Status.WEAK_PASSWORD, result);
    }

    @Test
    void okChangesPasswordAndClearsFlag() {
        PasswordChange.Status result = passwordChange.changePassword("admin", "newSecret1!");

        assertEquals(PasswordChange.Status.OK, result);

        Roles oldPassword = auth.checkLogin("admin", "secret");
        assertEquals(Authenticator.Status.WRONG_PASSWORD, oldPassword.status);

        Roles newPassword = auth.checkLogin("admin", "newSecret1!");
        assertEquals(Authenticator.Status.OK, newPassword.status);
        assertFalse(newPassword.mustChangePassword, "flag should be cleared after a successful change");
    }
}

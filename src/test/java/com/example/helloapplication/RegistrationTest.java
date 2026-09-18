package com.example.helloapplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationTest {

    @TempDir
    Path tempDir;

    private Database database;
    private Registration registration;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("users.db"));
        database.init();
        registration = new Registration(database);
    }

    @Test
    void emptyWhenNameBlank() {
        Registration.Status result = registration.createAccount("  ", "new@example.com", "hunter2");

        assertEquals(Registration.Status.EMPTY, result);
    }

    @Test
    void emptyWhenEmailBlank() {
        Registration.Status result = registration.createAccount("New User", "", "hunter2");

        assertEquals(Registration.Status.EMPTY, result);
    }

    @Test
    void emptyWhenPasswordBlank() {
        Registration.Status result = registration.createAccount("New User", "new@example.com", "");

        assertEquals(Registration.Status.EMPTY, result);
    }

    @Test
    void createsUserThatCanLogIn() {
        Registration.Status result = registration.createAccount("New User", "new@example.com", "hunter2!");

        assertEquals(Registration.Status.OK, result);

        Authenticator auth = new Authenticator(database);
        Roles login = auth.checkLogin("new@example.com", "hunter2!");

        assertEquals(Authenticator.Status.OK, login.status);
        assertEquals("USER", login.roleName);
        assertFalse(login.isAdmin());
        assertFalse(login.mustChangePassword, "self-registered accounts shouldn't be forced to change their password");
    }

    @Test
    void duplicateEmailRejected() {
        registration.createAccount("New User", "new@example.com", "hunter2!");
        Registration.Status result = registration.createAccount("Someone Else", "new@example.com", "different1!");

        assertEquals(Registration.Status.DUPLICATE, result);
    }

    @Test
    void weakWhenPasswordTooShort() {
        Registration.Status result = registration.createAccount("New User", "new@example.com", "sh0rt!");

        assertEquals(Registration.Status.WEAK_PASSWORD, result);
    }

    @Test
    void weakWhenPasswordMissingDigit() {
        Registration.Status result = registration.createAccount("New User", "new@example.com", "nodigits!");

        assertEquals(Registration.Status.WEAK_PASSWORD, result);
    }

    @Test
    void weakWhenPasswordMissingSymbol() {
        Registration.Status result = registration.createAccount("New User", "new@example.com", "nosymbol1");

        assertEquals(Registration.Status.WEAK_PASSWORD, result);
    }
}

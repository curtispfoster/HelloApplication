package com.example.helloapplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OwnerSetupTest {

    @TempDir
    Path tempDir;

    private Database database;
    private OwnerSetup ownerSetup;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("users.db"));
        database.init(); // no accounts
        ownerSetup = new OwnerSetup(database);
    }

    @Test
    void neededOnFreshDatabase() {
        assertTrue(ownerSetup.isNeeded());
    }

    @Test
    void notNeededOnceOwnerCreated() {
        OwnerSetup.Status result = ownerSetup.createOwner("curtis", "Passw0rd!");

        assertEquals(OwnerSetup.Status.OK, result);
        assertFalse(ownerSetup.isNeeded());
    }

    @Test
    void secondOwnerIsRefused() {
        ownerSetup.createOwner("curtis", "Passw0rd!");

        OwnerSetup.Status result = ownerSetup.createOwner("intruder", "Passw0rd!");

        assertEquals(OwnerSetup.Status.ALREADY_SET_UP, result);
        Roles login = new Authenticator(database).checkLogin("intruder", "Passw0rd!");
        assertNotEquals(Authenticator.Status.OK, login.status);
    }

    @Test
    void emptyWhenUsernameBlank() {
        assertEquals(OwnerSetup.Status.EMPTY, ownerSetup.createOwner("  ", "Passw0rd!"));
        assertTrue(ownerSetup.isNeeded());
    }

    @Test
    void emptyWhenPasswordBlank() {
        assertEquals(OwnerSetup.Status.EMPTY, ownerSetup.createOwner("curtis", ""));
    }

    @Test
    void weakWhenPasswordDoesNotMeetPolicy() {
        assertEquals(OwnerSetup.Status.WEAK_PASSWORD, ownerSetup.createOwner("curtis", "short"));
        assertTrue(ownerSetup.isNeeded());
    }

    @Test
    void duplicateWhenUsernameTakenByUser() throws Exception {
        TestAccounts.add(database, "curtis", "Passw0rd!", Role.USER, false);

        assertEquals(OwnerSetup.Status.DUPLICATE, ownerSetup.createOwner("curtis", "Other1pass!"));
        assertTrue(ownerSetup.isNeeded());
    }

    @Test
    void neededWhenOnlyAdminsExist() throws Exception {
        TestAccounts.add(database, "admin", "Rotated1!", Role.ADMIN, false);

        assertTrue(ownerSetup.isNeeded());
    }

    @Test
    void createdOwnerCanLogInWithoutForcedChange() {
        ownerSetup.createOwner("  curtis  ", "Passw0rd!");

        Roles login = new Authenticator(database).checkLogin("curtis", "Passw0rd!");

        assertEquals(Authenticator.Status.OK, login.status);
        assertTrue(login.isOwner());
        assertFalse(login.mustChangePassword, "the owner chose this password, so no forced change");
    }
}

package com.example.helloapplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    @TempDir
    Path tempDir;

    @Test
    void initCreatesFileAndEmptyUsersTable() throws Exception {
        Path dbFile = tempDir.resolve("users.db");
        Database database = new Database(dbFile);

        database.init();

        assertTrue(java.nio.file.Files.exists(dbFile));
        assertEquals(0, userCount(database), "no accounts are seeded; the owner is created on first run");
    }

    @Test
    void initIsSafeToRunTwice() throws Exception {
        Path dbFile = tempDir.resolve("users.db");
        Database database = new Database(dbFile);

        database.init();
        TestAccounts.add(database, "someone", "Passw0rd!", Role.USER, false);
        database.init();

        assertEquals(1, userCount(database));
    }

    @Test
    void initRemovesSeedAccountsStillOnDefaultPassword() throws Exception {
        Database database = databaseAtSeedVersion1();
        TestAccounts.seedAdminAndOwner(database);

        database.init();

        assertFalse(userExists(database, "admin"), "admin still on 'secret' should be removed");
        assertFalse(userExists(database, "owner"), "owner still on 'changeme' should be removed");
    }

    @Test
    void initKeepsSeedAccountWhosePasswordWasChanged() throws Exception {
        Database database = databaseAtSeedVersion1();
        TestAccounts.add(database, "admin", "Rotated1!", Role.ADMIN, false);
        TestAccounts.add(database, "owner", "changeme", Role.OWNER, true);

        database.init();

        assertTrue(userExists(database, "admin"), "an admin whose password was changed must be kept");
        assertFalse(userExists(database, "owner"));
    }

    @Test
    void initReconcilesStaleAdminFromPreMustChangePasswordSchema() throws Exception {
        Path dbFile = tempDir.resolve("users.db");

        // Build the old schema by hand: no MustChangePassword column at all,
        // and an admin row that predates it (mirrors a real pre-fix data/users.db).
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath())) {
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        CREATE TABLE Users (
                            UserID   INTEGER PRIMARY KEY AUTOINCREMENT,
                            Username TEXT    NOT NULL UNIQUE,
                            Password TEXT    NOT NULL,
                            Role     TEXT    NOT NULL DEFAULT 'USER'
                        )
                        """);
            }
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO Users (Username, Password, Role) VALUES ('admin', 'irrelevant-hash', 'ADMIN')")) {
                insert.executeUpdate();
            }
        }

        new Database(dbFile).init();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT MustChangePassword FROM Users WHERE Username = 'admin'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("MustChangePassword"),
                    "a pre-existing admin row from before MustChangePassword existed must be reconciled to force a change");
        }
    }

    @Test
    void initDoesNotReForceFlagAfterLegitimatePasswordChange() throws Exception {
        Path dbFile = tempDir.resolve("users.db");
        Database database = new Database(dbFile);

        database.init();
        TestAccounts.add(database, "admin", "secret", Role.ADMIN, true);
        new PasswordChange(database).changePassword("admin", "NewPassw0rd!"); // legitimately clears the flag

        database.init(); // simulates a later app startup

        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT MustChangePassword FROM Users WHERE Username = 'admin'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("MustChangePassword"),
                    "a legitimate self-service password change must not be re-forced by a later init()");
        }
    }

    @Test
    void initSetsSeedVersionOnceAndDoesNotReapplyOnSecondInit() throws Exception {
        Path dbFile = tempDir.resolve("users.db");
        Database database = new Database(dbFile);

        database.init();
        database.init();

        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    // A database already caught up to version 1 (as it would be before accounts
    // stopped being seeded), so the next init() runs only the version 2 step.
    private Database databaseAtSeedVersion1() throws Exception {
        Database database = new Database(tempDir.resolve("users.db"));
        database.init();
        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("PRAGMA user_version = 1");
        }
        return database;
    }

    private int userCount(Database database) throws Exception {
        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Users")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private boolean userExists(Database database, String username) throws Exception {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Users WHERE Username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
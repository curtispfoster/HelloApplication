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
    void initCreatesFileTableAndAdmin() throws Exception {
        Path dbFile = tempDir.resolve("users.db");
        Database database = new Database(dbFile);

        database.init();

        assertTrue(java.nio.file.Files.exists(dbFile));

        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT Username, Role, MustChangePassword FROM Users WHERE Username = 'admin'")) {

            assertTrue(rs.next(), "admin row should exist");
            assertEquals("ADMIN", rs.getString("Role"));
            assertEquals(1, rs.getInt("MustChangePassword"), "seeded admin should be flagged to change its password");
            assertFalse(rs.next(), "should be only one admin seed row");
        }
    }

    @Test
    void initSeedsOwner() throws Exception {
        Path dbFile = tempDir.resolve("users.db");
        Database database = new Database(dbFile);

        database.init();

        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT Username, Role, MustChangePassword FROM Users WHERE Username = 'owner'")) {

            assertTrue(rs.next(), "owner row should exist");
            assertEquals("OWNER", rs.getString("Role"));
            assertEquals(1, rs.getInt("MustChangePassword"), "seeded owner should be flagged to change its password");
            assertFalse(rs.next(), "should be only one owner seed row");
        }
    }

    @Test
    void initIsSafeToRunTwice() throws Exception {
        Path dbFile = tempDir.resolve("users.db");
        Database database = new Database(dbFile);

        database.init();
        database.init();

        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Users")) {

            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
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

        database.init(); // fresh install: admin seeded with MustChangePassword=1
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
            assertEquals(1, rs.getInt(1));
        }
    }
}
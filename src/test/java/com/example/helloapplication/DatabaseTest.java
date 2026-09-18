package com.example.helloapplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
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
}
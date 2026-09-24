package com.example.helloapplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * First-run setup: no accounts are seeded, so the first person to launch the app
 * creates the OWNER. Only ever creates one; once an OWNER exists this does nothing.
 */
public class OwnerSetup {

    private static final Logger LOGGER = Logger.getLogger(OwnerSetup.class.getName());

    public enum Status { EMPTY, WEAK_PASSWORD, DUPLICATE, ALREADY_SET_UP, ERROR, OK }

    private final Database database;

    public OwnerSetup(Database database) {
        this.database = database;
    }

    public boolean isNeeded() {
        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM Users WHERE Role = 'OWNER' LIMIT 1")) {
            return !rs.next();
        } catch (SQLException e) {
            // Let the login screen report the database problem instead.
            LOGGER.log(Level.SEVERE, "Owner check failed", e);
            return false;
        }
    }

    public Status createOwner(String username, String password) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            return Status.EMPTY;
        }

        if (!PasswordPolicy.meetsRequirements(password)) {
            return Status.WEAK_PASSWORD;
        }

        // One statement, so a second owner can never be created even if two
        // setup screens are submitted.
        String sql = """
                INSERT INTO Users (Username, Password, Role, MustChangePassword)
                SELECT ?, ?, 'OWNER', 0
                WHERE NOT EXISTS (SELECT 1 FROM Users WHERE Role = 'OWNER')
                """;

        char[] pwd = password.trim().toCharArray();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            ps.setString(2, Argon2PasswordHasher.hash(pwd));
            return ps.executeUpdate() == 1 ? Status.OK : Status.ALREADY_SET_UP;

        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                return Status.DUPLICATE;
            }
            LOGGER.log(Level.SEVERE, "Owner setup failed", e);
            return Status.ERROR;
        } finally {
            Arrays.fill(pwd, '\0');
        }
    }

    private boolean isUniqueViolation(SQLException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("UNIQUE constraint failed");
    }
}

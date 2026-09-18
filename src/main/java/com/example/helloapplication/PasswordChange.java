package com.example.helloapplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lets a signed-in account set a new password, clearing the
 * MustChangePassword flag set on seeded accounts.
 */
public class PasswordChange {

    public enum Status { EMPTY, WEAK_PASSWORD, ERROR, OK }

    private static final Logger LOGGER = Logger.getLogger(PasswordChange.class.getName());

    private final Database database;

    public PasswordChange(Database database) {
        this.database = database;
    }

    public Status changePassword(String username, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            return Status.EMPTY;
        }
        if (!PasswordPolicy.meetsRequirements(newPassword)) {
            return Status.WEAK_PASSWORD;
        }

        String sql = "UPDATE Users SET Password = ?, MustChangePassword = 0 WHERE Username = ?";

        char[] pwd = newPassword.trim().toCharArray();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, Argon2PasswordHasher.hash(pwd));
            ps.setString(2, username);
            ps.executeUpdate();
            return Status.OK;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Password change failed", e);
            return Status.ERROR;
        } finally {
            Arrays.fill(pwd, '\0');
        }
    }
}

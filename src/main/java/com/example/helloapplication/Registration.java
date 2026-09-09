package com.example.helloapplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Creates new accounts for the create-account UI.
 * The entered email is stored as Username, since the login screen accepts
 * "Username or Email". Accounts created here are always Role 'USER' —
 * this flow does not create admins.
 */
public class Registration {

    public enum Status { EMPTY, WEAK_PASSWORD, DUPLICATE, ERROR, OK }

    private final Database database;

    public Registration(Database database) {
        this.database = database;
    }

    public Status createAccount(String name, String email, String password) {
        if (name == null || name.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return Status.EMPTY;
        }

        if (!PasswordPolicy.meetsRequirements(password)) {
            return Status.WEAK_PASSWORD;
        }

        String sql = "INSERT INTO Users (Username, Password, Role, Name) VALUES (?, ?, 'USER', ?)";

        char[] pwd = password.trim().toCharArray();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email.trim());
            ps.setString(2, Argon2PasswordHasher.hash(pwd));
            ps.setString(3, name.trim());
            ps.executeUpdate();
            return Status.OK;

        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                return Status.DUPLICATE;
            }
            e.printStackTrace();
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

package com.example.helloapplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Test fixtures: Database.init() no longer seeds any accounts. */
final class TestAccounts {

    private TestAccounts() {
    }

    static void add(Database database, String username, String password, Role role, boolean mustChangePassword)
            throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO Users (Username, Password, Role, MustChangePassword) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, Argon2PasswordHasher.hash(password.toCharArray()));
            ps.setString(3, role.name());
            ps.setInt(4, mustChangePassword ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /** admin/secret (ADMIN) and owner/changeme (OWNER), both flagged to change password. */
    static void seedAdminAndOwner(Database database) throws SQLException {
        add(database, "admin", "secret", Role.ADMIN, true);
        add(database, "owner", "changeme", Role.OWNER, true);
    }
}

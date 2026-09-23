package com.example.helloapplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserManagement {

    private static final Logger LOGGER = Logger.getLogger(UserManagement.class.getName());

    public enum Status { OK, FORBIDDEN, NOT_FOUND, ERROR }

    public record UserSummary(String username, Role role, boolean mustChangePassword) {}

    private final Database database;

    public UserManagement(Database database) {
        this.database = database;
    }

    public List<UserSummary> listUsers() {
        List<UserSummary> users = new ArrayList<>();
        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT Username, Role, MustChangePassword FROM Users ORDER BY Username")) {

            while (rs.next()) {
                users.add(new UserSummary(
                        rs.getString("Username"),
                        Role.fromString(rs.getString("Role")),
                        rs.getInt("MustChangePassword") != 0));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "List users failed", e);
        }
        return users;
    }

    public Status deleteUser(Roles actor, String targetUsername) {
        if (actor == null || !actor.isAdmin()) {
            return Status.FORBIDDEN;
        }

        try (Connection conn = database.getConnection()) {
            Role targetRole = lookupRole(conn, targetUsername);
            if (targetRole == null) {
                return Status.NOT_FOUND;
            }
            if (targetRole == Role.OWNER && !actor.isOwner()) {
                return Status.FORBIDDEN;
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM Users WHERE Username = ?")) {
                ps.setString(1, targetUsername);
                ps.executeUpdate();
            }
            return Status.OK;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Delete user failed", e);
            return Status.ERROR;
        }
    }

    private Role lookupRole(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT Role FROM Users WHERE Username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return Role.fromString(rs.getString("Role"));
            }
        }
    }
}

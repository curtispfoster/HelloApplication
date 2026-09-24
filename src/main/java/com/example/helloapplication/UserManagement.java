package com.example.helloapplication;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserManagement {

    private static final Logger LOGGER = Logger.getLogger(UserManagement.class.getName());

    // Excludes visually confusing characters (I, l, 1, O, 0).
    private static final String TEMP_LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String TEMP_DIGITS = "23456789";
    private static final String TEMP_SYMBOLS = "!@#$%&*?";
    private static final int TEMP_PASSWORD_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Status { OK, FORBIDDEN, NOT_FOUND, ERROR }

    public record UserSummary(String username, Role role, boolean mustChangePassword) {}

    /** {@code temporaryPassword} is null unless {@code status} is {@link Status#OK}. */
    public record ResetResult(Status status, String temporaryPassword) {}

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
        if (targetUsername != null && targetUsername.equals(actor.username)) {
            return Status.FORBIDDEN;
        }

        try (Connection conn = database.getConnection()) {
            Role targetRole = lookupRole(conn, targetUsername);
            if (targetRole == null) {
                return Status.NOT_FOUND;
            }
            if (targetRole == Role.OWNER) {
                if (!actor.isOwner()) {
                    return Status.FORBIDDEN;
                }
                if (isLastOwner(conn)) {
                    return Status.FORBIDDEN;
                }
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

    /**
     * Sets a random temporary password, guarded the same way as {@link #deleteUser}
     * (an ADMIN can't reset an OWNER's password; only another OWNER can). The account
     * is flagged {@code MustChangePassword} so the forced-change flow picks it up at
     * the target's next login.
     */
    public ResetResult resetPassword(Roles actor, String targetUsername) {
        if (actor == null || !actor.isAdmin()) {
            return new ResetResult(Status.FORBIDDEN, null);
        }

        try (Connection conn = database.getConnection()) {
            Role targetRole = lookupRole(conn, targetUsername);
            if (targetRole == null) {
                return new ResetResult(Status.NOT_FOUND, null);
            }
            if (targetRole == Role.OWNER && !actor.isOwner()) {
                return new ResetResult(Status.FORBIDDEN, null);
            }

            String temporaryPassword = generateTemporaryPassword();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE Users SET Password = ?, MustChangePassword = 1 WHERE Username = ?")) {
                ps.setString(1, Argon2PasswordHasher.hash(temporaryPassword.toCharArray()));
                ps.setString(2, targetUsername);
                ps.executeUpdate();
            }
            return new ResetResult(Status.OK, temporaryPassword);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Reset password failed", e);
            return new ResetResult(Status.ERROR, null);
        }
    }

    // Always includes a digit and a symbol, so it meets PasswordPolicy by construction.
    private static String generateTemporaryPassword() {
        List<Character> chars = new ArrayList<>();
        chars.add(TEMP_DIGITS.charAt(RANDOM.nextInt(TEMP_DIGITS.length())));
        chars.add(TEMP_SYMBOLS.charAt(RANDOM.nextInt(TEMP_SYMBOLS.length())));
        String pool = TEMP_LETTERS + TEMP_DIGITS;
        while (chars.size() < TEMP_PASSWORD_LENGTH) {
            chars.add(pool.charAt(RANDOM.nextInt(pool.length())));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder password = new StringBuilder(chars.size());
        chars.forEach(password::append);
        return password.toString();
    }

    private boolean isLastOwner(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Users WHERE Role = 'OWNER'")) {
            rs.next();
            return rs.getInt(1) <= 1;
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

package com.example.helloapplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    public static final Path USERS_FILE = AppPaths.DATA_DIR.resolve("users.db");

    private static final int CURRENT_SEED_VERSION = 2;

    private static Database users;

    private final Path dbFile;
    private final String url;

    public Database(Path dbFile){
        this.dbFile = dbFile;
        this.url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
    }

    public static synchronized Database users() throws SQLException {
        if (users == null) {
            Database database = new Database(USERS_FILE);
            database.init();
            users = database;
        }
        return users;
    }

    public Connection getConnection() throws SQLException{
        return DriverManager.getConnection(url);
    }

    public void init() throws SQLException{
            try {
                if(dbFile.getParent() != null){
                    Files.createDirectories(dbFile.getParent());
                }
            } catch(Exception e){
                throw new SQLException("Could not create data folder: " + dbFile.getParent(), e);
            }


        String create = """
                CREATE TABLE IF NOT EXISTS Users (
                    UserID              INTEGER PRIMARY KEY AUTOINCREMENT,
                    Username            TEXT    NOT NULL UNIQUE,
                    Password            TEXT    NOT NULL,
                    Role                TEXT    NOT NULL DEFAULT 'USER',
                    Name                TEXT,
                    MustChangePassword  INTEGER NOT NULL DEFAULT 0
                )
                """;

        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute(create);
            }
            ensureNameColumn(conn);
            ensureMustChangePasswordColumn(conn);
            migratePlaintextPasswords(conn);
            reconcileSeedAccounts(conn);
        }
    }

    private void ensureNameColumn(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(Users)")) {
            while (rs.next()) {
                if ("Name".equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE Users ADD COLUMN Name TEXT");
        }
    }

    private void ensureMustChangePasswordColumn(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(Users)")) {
            while (rs.next()) {
                if ("MustChangePassword".equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE Users ADD COLUMN MustChangePassword INTEGER NOT NULL DEFAULT 0");
        }
    }

    private void migratePlaintextPasswords(Connection conn) throws SQLException {
        try (Statement select = conn.createStatement();
             ResultSet rs = select.executeQuery(
                     "SELECT UserID, Password FROM Users WHERE Password NOT GLOB '$argon2id$*'");
             PreparedStatement update = conn.prepareStatement(
                     "UPDATE Users SET Password = ? WHERE UserID = ?")) {

            while (rs.next()) {
                String stored = rs.getString("Password");
                if (stored != null && !stored.startsWith("$argon2id$")) {
                    update.setString(1, Argon2PasswordHasher.hash(stored.toCharArray()));
                    update.setInt(2, rs.getInt("UserID"));
                    update.executeUpdate();
                }
            }
        }
    }

    private int seedVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setSeedVersion(Connection conn, int version) throws SQLException {
        // PRAGMA doesn't accept a bound "?" parameter here; version is always
        // our own int constant, never external input, so concatenation is safe.
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA user_version = " + version);
        }
    }

    // Version 1: MustChangePassword was added after the admin/owner seed rows
    // may already have existed on a local users.db, so ensureMustChangePasswordColumn's
    // ALTER ... DEFAULT 0 left those rows silently stuck at 0 instead of ever being
    // forced through the change-password flow once, unlike a fresh install (which
    // always inserts them with MustChangePassword=1). Runs once per database file;
    // never touches a row again once its version is caught up, so a legitimate
    // self-service password change (PasswordChange.changePassword) is never undone.
    //
    // Version 2: accounts are no longer seeded (the first owner is created on
    // OwnerSetup_View), so an admin/owner row still on its old published default
    // password is an open door. Removing it is safe: nobody has used it, since the
    // forced change would have replaced that password.
    private void reconcileSeedAccounts(Connection conn) throws SQLException {
        int version = seedVersion(conn);
        if (version >= CURRENT_SEED_VERSION) {
            return;
        }
        if (version < 1) {
            forceChangeOnNextLogin(conn, "admin");
            forceChangeOnNextLogin(conn, "owner");
        }
        if (version < 2) {
            // These literals only detect never-rotated rows from before version 2;
            // nothing creates an account with them anymore.
            removeIfStillOnPassword(conn, "admin", "secret");
            removeIfStillOnPassword(conn, "owner", "changeme");
        }
        setSeedVersion(conn, CURRENT_SEED_VERSION);
    }

    private void removeIfStillOnPassword(Connection conn, String username, String password) throws SQLException {
        String stored;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT Password FROM Users WHERE Username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                stored = rs.getString("Password");
            }
        }
        if (!Argon2PasswordHasher.verify(password.toCharArray(), stored)) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM Users WHERE Username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }

    private void forceChangeOnNextLogin(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE Users SET MustChangePassword = 1 WHERE Username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }
}

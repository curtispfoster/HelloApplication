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
    private final Path dbFile;
    private final String url;

    public Database(Path dbFile){
        this.dbFile = dbFile;
        this.url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
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
                    UserID   INTEGER PRIMARY KEY AUTOINCREMENT,
                    Username TEXT    NOT NULL UNIQUE,
                    Password TEXT    NOT NULL,
                    Role     TEXT    NOT NULL DEFAULT 'USER',
                    Name     TEXT
                )
                """;

        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute(create);
            }
            ensureNameColumn(conn);
            seedAdmin(conn);
            seedOwner(conn);
            migratePlaintextPasswords(conn);
        }
    }

    /**
     * Tables created before the Name column existed need it added in place;
     * CREATE TABLE IF NOT EXISTS won't alter an already-existing table.
     */
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

    private void seedAdmin(Connection conn) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement(
                "SELECT 1 FROM Users WHERE Username = ?")) {
            check.setString(1, "admin");
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO Users (Username, Password, Role) VALUES (?, ?, 'ADMIN')")) {
            insert.setString(1, "admin");
            insert.setString(2, Argon2PasswordHasher.hash("secret".toCharArray()));
            insert.executeUpdate();
        }
    }

    /**
     * Seeds the one protected OWNER account. Unlike ADMIN, OWNER is never
     * deletable or demotable by an ADMIN (see UserManagement) — this is the
     * root account the software owner uses to manage admins and licensing.
     */
    private void seedOwner(Connection conn) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement(
                "SELECT 1 FROM Users WHERE Username = ?")) {
            check.setString(1, "owner");
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO Users (Username, Password, Role) VALUES (?, ?, 'OWNER')")) {
            insert.setString(1, "owner");
            insert.setString(2, Argon2PasswordHasher.hash("changeme".toCharArray()));
            insert.executeUpdate();
        }
    }

    /**
     * Older rows created before Argon2id was introduced still hold plaintext
     * passwords. Since the stored value IS the plaintext in that case, it can
     * be hashed in place; anything already Argon2id-encoded is left alone.
     */
    private void migratePlaintextPasswords(Connection conn) throws SQLException {
        try (Statement select = conn.createStatement();
             ResultSet rs = select.executeQuery("SELECT UserID, Password FROM Users");
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
}

package com.example.helloapplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Authenticator {

    private static final Logger LOGGER = Logger.getLogger(Authenticator.class.getName());

    public enum Status { EMPTY, WRONG, WRONG_PASSWORD, ERROR, OK }

    private final Database database;


    public Authenticator(Database database) {
        this.database = database;
    }

    public Roles checkLogin(String enteredUser, String enteredPwd) {
        // Gate: require both fields before comparing
        if (enteredUser == null || enteredUser.isBlank()
                || enteredPwd == null || enteredPwd.isBlank()) {
            return new Roles(Status.EMPTY, null, null);
        }

        String sql = "SELECT Username, Password, Role, MustChangePassword FROM Users WHERE Username = ?";

        try(Connection conn = database.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){

            ps.setString(1, enteredUser.trim());
            try (ResultSet rs = ps.executeQuery()){
                if(!rs.next()){
                    return new Roles(Status.WRONG, null, null);
                }

                char[] attempt = enteredPwd.trim().toCharArray();
                boolean matches;
                try {
                    matches = Argon2PasswordHasher.verify(attempt, rs.getString("Password"));
                } finally {
                    Arrays.fill(attempt, '\0');
                }

                if (!matches) {
                    return new Roles(Status.WRONG_PASSWORD, null, null);
                }
                boolean mustChangePassword = rs.getInt("MustChangePassword") != 0;
                return new Roles(Status.OK, rs.getString("Username"), rs.getString("Role"), mustChangePassword);
            }

        } catch(SQLException e){
            LOGGER.log(Level.SEVERE, "Login lookup failed", e);
            return new Roles(Status.ERROR, null, null);
        }

    }
}

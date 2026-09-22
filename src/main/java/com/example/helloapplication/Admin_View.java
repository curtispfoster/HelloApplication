package com.example.helloapplication;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Landing screen for a signed-in ADMIN or OWNER account.
 * Placeholder for now — no user-management UI wired in yet (see
 * UserManagement); this just proves admin logins route here instead of
 * the plain Home_View.
 */
public class Admin_View {

    private static final Logger LOGGER = Logger.getLogger(Admin_View.class.getName());

    private final String username;

    public Admin_View(String username) {
        this.username = username;
    }

    public void show(Stage primaryStage) {
        Label welcome = buildWelcomeLabel();
        Button changePassword = buildChangePasswordButton(primaryStage);
        Button logout = buildLogoutButton(primaryStage);
        VBox root = buildRoot(welcome, changePassword, logout);

        primaryStage.setTitle("Admin");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    private Label buildWelcomeLabel() {
        return new Label("Welcome, " + username + " (admin).");
    }

    /** Swaps this window over to ChangePassword_View (not forced — Cancel returns here). */
    private Button buildChangePasswordButton(Stage primaryStage) {
        Button changePassword = new Button("Change password");
        changePassword.setOnAction(e -> {
            try {
                new ChangePassword_View(username, true, false).show(primaryStage);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Could not open ChangePassword_View", ex);
            }
        });
        return changePassword;
    }

    /** Swaps this window back to Login_View. */
    private Button buildLogoutButton(Stage primaryStage) {
        Button logout = new Button("Log out");
        logout.setOnAction(e -> {
            try {
                new Login_View().show(primaryStage);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Could not open Login_View", ex);
            }
        });
        return logout;
    }

    private VBox buildRoot(Label welcome, Button changePassword, Button logout) {
        VBox root = new VBox(16, welcome, changePassword, logout);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setPrefWidth(400);
        root.setPrefHeight(250);
        return root;
    }
}

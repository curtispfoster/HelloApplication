package com.example.helloapplication;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.sql.SQLException;
//Todo — next up:
//1. handleLogin() already returns Roles, but Login_View discards it (see login_btn.setOnAction below).
//   Route OK logins to an Admin view vs a Home view based on Roles.isAdmin()/isOwner() instead of
//   staying on this screen.
//2. Wire the "Forgot Password?" link — currently has no handler.
//3. Build an admin screen that calls UserManagement.deleteUser(...) — the role-guarded delete logic
//   exists (OWNER protected from ADMIN deletion) but no UI calls it yet.
//4. Change the seeded admin/secret and owner/changeme passwords before any real use (see README).
//Refer to "Adding Database README.md" in documentation directory for schema details.
//When completed or stopped for the day update "Coding Journal.md"

/**
 * FYI:
 * Run VM options (IntelliJ → Edit Configurations → VM options),
 * also duplicated in pom.xml javafx-maven-plugin:
 *   --enable-native-access=javafx.graphics
 *   --sun-misc-unsafe-memory-access=allow
 * First flag: allow JavaFX to load native window/graphics libs (JEP 472).
 * Second flag: silence Marlin Unsafe warning on JDK 24+ with JavaFX 21.
 * Maven javafx:run already has these; IDE Run does not inherit them.


/**
 * Native JavaFX login window (no FXML).
 * Builds a centered form with username, password (+ forgot link), login button, and status label.
 * Login attempts are delegated to {@link LoginController} using {@link Authenticator}.
 */
public class Login_View extends Application {

    /**
     * Builds the login UI and shows the primary stage.
     * Layout (top → bottom): username block, password block, login button, status label.
     */
    @Override
    public void start(Stage primaryStage) {
        // --- Username block: label + text field ---
        Label usernameLabel = new Label("Username or Email");
        TextField usernameField = new TextField();
        usernameField.setMaxWidth(200);

        VBox usernameBox = new VBox(6, usernameLabel, usernameField); // 6px spacing between children
        usernameBox.setAlignment(Pos.CENTER);

        // --- Password block: label + forgot link, then masked field ---
        Label passwordLabel = new Label("Password");
        Hyperlink forgotLink = new Hyperlink("Forgot Password?"); // no handler wired yet

        HBox passwordHeader = new HBox(10, passwordLabel, forgotLink);
        passwordHeader.setAlignment(Pos.CENTER);

        PasswordField passwordField = new PasswordField();
        passwordField.setMaxWidth(200);

        VBox passwordBox = new VBox(6, passwordHeader, passwordField);
        passwordBox.setAlignment(Pos.CENTER);

        // --- Login button ---
        Button login_btn = new Button("Login");
        HBox login_btn_hbox = new HBox(login_btn);
        login_btn_hbox.setAlignment(Pos.CENTER);

        // --- Create-account link, shown under the login button ---
        Hyperlink createAccountLink = new Hyperlink("Create an account");
        createAccountLink.setOnAction(e -> {
            try {
                primaryStage.close();
                new Create_View().start(new Stage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        HBox createAccountBox = new HBox(createAccountLink);
        createAccountBox.setAlignment(Pos.CENTER);

        // --- Feedback label: updated by LoginController after each attempt ---
        Label login_status = new Label("Login status message");

        // Demo credentials for Authenticator (plain text until hashing is added)
        //Authenticator auth = new Authenticator("Admin", "Secret");
        // Controller owns the login flow: read fields → checkLogin → set status text
        Path dbFile = Path.of("data", "users.db");
        System.out.println("DB: " + dbFile.toAbsolutePath());

        Database database = new Database(dbFile);
        try {
            database.init();
        } catch (SQLException e) {
            e.printStackTrace();
            login_status.setText("Could not reach the database.");
        }


        Authenticator auth = new Authenticator(database);
        LoginController controller = new LoginController(
                auth, usernameField, passwordField, login_status
        );
        // Trigger login on button click or Enter in either field
        login_btn.setOnAction(e -> controller.handleLogin());
        usernameField.setOnAction(e -> controller.handleLogin());
        passwordField.setOnAction(e -> controller.handleLogin());

        // --- Root layout: stacks all sections, sized like the earlier FXML (~400×250) ---
        VBox root_box = new VBox(12, usernameBox, passwordBox, login_btn_hbox, createAccountBox, login_status);
        root_box.setAlignment(Pos.TOP_CENTER);
        root_box.setPadding(new Insets(24));
        root_box.setPrefWidth(400);
        root_box.setPrefHeight(250);

        primaryStage.setTitle("Login");
        primaryStage.setScene(new Scene(root_box));
        primaryStage.show();
    }
}
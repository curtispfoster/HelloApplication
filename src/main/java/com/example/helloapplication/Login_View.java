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
//1. Wire the "Forgot Password?" link — currently has no handler.
//2. Admin_View/Home_View are placeholders (welcome label + logout). Build an admin screen that calls
//   UserManagement.deleteUser(...) — the role-guarded delete logic exists (OWNER protected from ADMIN
//   deletion) but no UI calls it yet.
//3. Change the seeded admin/secret and owner/changeme passwords before any real use (see README).
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
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Label loginStatus = new Label("Login status message");

        VBox usernameBox = buildUsernameBox(usernameField);
        VBox passwordBox = buildPasswordBox(passwordField);

        Database database = openDatabase(loginStatus);
        Authenticator auth = new Authenticator(database);
        LoginController controller = new LoginController(auth, usernameField, passwordField, loginStatus);

        HBox loginButtonBox = buildLoginButtonBox(controller, usernameField, passwordField, primaryStage);
        HBox createAccountBox = buildCreateAccountBox(primaryStage);
        VBox root = buildRoot(usernameBox, passwordBox, loginButtonBox, createAccountBox, loginStatus);

        primaryStage.setTitle("Login");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /** Label + text field for username/email. */
    private VBox buildUsernameBox(TextField usernameField) {
        Label usernameLabel = new Label("Username or Email");
        usernameField.setMaxWidth(200);

        VBox usernameBox = new VBox(6, usernameLabel, usernameField); // 6px spacing between children
        usernameBox.setAlignment(Pos.CENTER);
        return usernameBox;
    }

    /** Label + forgot-password link header, then the masked password field. */
    private VBox buildPasswordBox(PasswordField passwordField) {
        Label passwordLabel = new Label("Password");
        Hyperlink forgotLink = new Hyperlink("Forgot Password?"); // no handler wired yet

        HBox passwordHeader = new HBox(10, passwordLabel, forgotLink);
        passwordHeader.setAlignment(Pos.CENTER);

        passwordField.setMaxWidth(200);

        VBox passwordBox = new VBox(6, passwordHeader, passwordField);
        passwordBox.setAlignment(Pos.CENTER);
        return passwordBox;
    }

    /** Opens (and initializes) the SQLite-backed Database, reporting failure via the status label. */
    private Database openDatabase(Label loginStatus) {
        Path dbFile = Path.of("data", "users.db");
        System.out.println("DB: " + dbFile.toAbsolutePath());

        Database database = new Database(dbFile);
        try {
            database.init();
        } catch (SQLException e) {
            e.printStackTrace();
            loginStatus.setText("Could not reach the database.");
        }
        return database;
    }

    /** Login button, wired to trigger on click or Enter in either field. */
    private HBox buildLoginButtonBox(LoginController controller, TextField usernameField,
                                      PasswordField passwordField, Stage primaryStage) {
        Button loginBtn = new Button("Login");
        loginBtn.setOnAction(e -> routeAfterLogin(controller.handleLogin(), primaryStage));
        usernameField.setOnAction(e -> routeAfterLogin(controller.handleLogin(), primaryStage));
        passwordField.setOnAction(e -> routeAfterLogin(controller.handleLogin(), primaryStage));

        HBox loginBtnBox = new HBox(loginBtn);
        loginBtnBox.setAlignment(Pos.CENTER);
        return loginBtnBox;
    }

    /** "Create an account" link: closes this window and opens Create_View. */
    private HBox buildCreateAccountBox(Stage primaryStage) {
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
        return createAccountBox;
    }

    /** Stacks all sections into the window's root layout, sized like the earlier FXML (~400x250). */
    private VBox buildRoot(VBox usernameBox, VBox passwordBox, HBox loginButtonBox,
                            HBox createAccountBox, Label loginStatus) {
        VBox root = new VBox(12, usernameBox, passwordBox, loginButtonBox, createAccountBox, loginStatus);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(24));
        root.setPrefWidth(400);
        root.setPrefHeight(250);
        return root;
    }

    /**
     * On a successful login, closes the login window and opens Admin_View
     * or Home_View depending on the account's role. On anything else
     * (EMPTY/WRONG/ERROR), the status label already shows why — stay put.
     */
    private void routeAfterLogin(Roles result, Stage primaryStage) {
        if (result.status != Authenticator.Status.OK) {
            return;
        }

        try {
            primaryStage.close();
            if (result.isAdmin()) {
                new Admin_View(result.username).start(new Stage());
            } else {
                new Home_View(result.username).start(new Stage());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

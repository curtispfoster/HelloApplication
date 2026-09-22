package com.example.helloapplication;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
//Todo — next up:
//1. Build the admin table in Admin_View: list from UserManagement.listUsers(), delete via the existing
//   role-guarded deleteUser (OWNER protected from ADMIN deletion), confirmation dialog before the call.
//2. Guards missing in deleteUser: an ADMIN can delete their own account, and an OWNER can delete the
//   last OWNER. Both leave a live session with no backing row. Add self-delete and last-owner checks.
//3. UserManagement.resetPassword(actor, target) — set a temp password, flip MustChangePassword=1, and
//   the existing forced-change flow handles the rest. This also closes (4): "Forgot?" becomes "ask an
//   admin" instead of needing SMTP, which suits a desktop app. Check the Coding Journal first — a
//   temp-password path was built and reverted once; find out why before reusing that shape.
//4. Wire the "Forgot Password?" link below — currently has no handler. See (3).
//5. Database.init()'s seedAdmin/seedOwner (and ensureXColumn migrations) only ever INSERT/ALTER — a row
//   created before a flag like MustChangePassword existed stays stale (e.g. MustChangePassword=0)
//   forever, since nothing re-checks or repairs already-existing rows on later init() calls. Add a
//   real check (e.g. a schema/seed version row, or explicit reconciliation for known seed accounts)
//   so stale local data can't silently diverge from what a fresh install would produce.
//
//Not a todo: Home_View is complete for its scope — a non-admin has nothing else to do here. And don't
//merge Home_View/Admin_View despite the near-identical bodies; (1) makes them diverge for real.

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
public class Login_View {

    private static final Logger LOGGER = Logger.getLogger(Login_View.class.getName());

    /**
     * Builds the login UI onto the given stage and shows it.
     * Layout (top → bottom): username block, password block, login button, status label.
     */
    public void show(Stage primaryStage) {
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        passwordField.setMaxWidth(200);
        PasswordVisibilityToggle passwordToggle = PasswordVisibilityToggle.wrap(passwordField);
        Label loginStatus = new Label("");
        loginStatus.setWrapText(true);
        loginStatus.setMaxWidth(320);
        StatusLabelAlignment.applyTo(loginStatus);

        VBox usernameBox = buildUsernameBox(usernameField);
        VBox passwordBox = buildPasswordBox(passwordToggle.field());

        Database database = openDatabase(loginStatus);
        Authenticator auth = new Authenticator(database);
        LoginController controller = new LoginController(auth, usernameField, passwordField, loginStatus);

        HBox loginButtonBox = buildLoginButtonBox(
                controller, usernameField, passwordField, passwordToggle.toggleButton(), primaryStage);
        HBox createAccountBox = buildCreateAccountBox(primaryStage);
        VBox root = buildRoot(usernameBox, passwordBox, loginButtonBox, createAccountBox, loginStatus);

        primaryStage.setTitle("Login");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /** Shared width for the username/password fields and the labels/links above them. */
    private static final double FIELD_WIDTH = 200;

    /** Label + text field for username/email. */
    private VBox buildUsernameBox(TextField usernameField) {
        Label usernameLabel = new Label("Email");
        usernameField.setMaxWidth(FIELD_WIDTH);
        usernameLabel.setMaxWidth(FIELD_WIDTH);
        VBox usernameBox = new VBox(6, usernameLabel, usernameField); // 6px spacing between children
        usernameBox.setAlignment(Pos.CENTER);
        return usernameBox;
    }

    /** Label + forgot-password link header, then the password field (show/hide toggle lives by the login button). */
    private VBox buildPasswordBox(StackPane passwordField) {
        passwordField.setMaxWidth(FIELD_WIDTH);

        Label passwordLabel = new Label("Password");
        // "Forgot Password?" doesn't fit next to "Password" within FIELD_WIDTH —
        // shortened so the header never needs more room than the field has.
        Hyperlink forgotLink = new Hyperlink("Forgot?");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox passwordHeader = new HBox(8, passwordLabel, spacer, forgotLink);
        passwordHeader.setAlignment(Pos.CENTER_LEFT);
        // Fixed cap, not a binding to the field's widthProperty: that property
        // only resolves after a layout pass, while the VBox needs the header's
        // preferred width before that pass to size itself — a circular
        // dependency that never converges and collapses the header to nothing.
        passwordHeader.setMaxWidth(FIELD_WIDTH);
        passwordHeader.setPrefWidth(FIELD_WIDTH);

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
            LOGGER.log(Level.SEVERE, "Could not initialize database", e);
            loginStatus.setText("Could not reach the database.");
        }
        return database;
    }

    /** Login button plus the password show/hide toggle, wired to trigger on click or Enter in either field. */
    private HBox buildLoginButtonBox(LoginController controller, TextField usernameField,
                                      PasswordField passwordField, ToggleButton showPasswordToggle,
                                      Stage primaryStage) {
        Button loginBtn = new Button("Login");
        loginBtn.setOnAction(e -> routeAfterLogin(controller.handleLogin(), primaryStage));
        usernameField.setOnAction(e -> routeAfterLogin(controller.handleLogin(), primaryStage));
        passwordField.setOnAction(e -> routeAfterLogin(controller.handleLogin(), primaryStage));

        HBox loginBtnBox = new HBox(10, loginBtn, showPasswordToggle);
        loginBtnBox.setAlignment(Pos.CENTER);
        return loginBtnBox;
    }

    /** "Create an account" link: swaps this window over to Create_View. */
    private HBox buildCreateAccountBox(Stage primaryStage) {
        Hyperlink createAccountLink = new Hyperlink("Create an account");
        createAccountLink.setOnAction(e -> {
            try {
                new Create_View().show(primaryStage);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Could not open Create_View", ex);
            }
        });
        HBox createAccountBox = new HBox(createAccountLink);
        createAccountBox.setAlignment(Pos.CENTER);
        return createAccountBox;
    }

    /** Stacks all sections into the window's root layout (~400x320 — tall enough that a populated status message doesn't get clipped). */
    private VBox buildRoot(VBox usernameBox, VBox passwordBox, HBox loginButtonBox,
                            HBox createAccountBox, Label loginStatus) {
        VBox root = new VBox(12, usernameBox, passwordBox, loginButtonBox, createAccountBox, loginStatus);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setPrefWidth(400);
        root.setPrefHeight(320);
        return root;
    }

    /**
     * On a successful login, swaps this window over to Admin_View or
     * Home_View depending on the account's role — or, if the account
     * still has its seeded default password, ChangePassword_View first
     * (forced, no Cancel) before either of those. On anything else
     * (EMPTY/WRONG/ERROR), the status label already shows why — stay put.
     */
    private void routeAfterLogin(Roles result, Stage primaryStage) {
        if (result.status != Authenticator.Status.OK) {
            return;
        }

        try {
            if (result.mustChangePassword) {
                new ChangePassword_View(result, true).show(primaryStage);
            } else if (result.isAdmin()) {
                new Admin_View(result).show(primaryStage);
            } else {
                new Home_View(result.username).show(primaryStage);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not open next view after login", ex);
        }
    }
}

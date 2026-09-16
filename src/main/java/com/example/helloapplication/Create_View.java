package com.example.helloapplication;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Native JavaFX create-account window (no FXML).
 * Mirrors the layout of Documents/FXML/Create-View.fxml: a centered form with
 * name, email, and password fields, then a Create Account button.
 * Account creation is delegated to {@link CreateAccountController} using
 * {@link Registration}. Per-field icons from the FXML (Gluon Glisten Icon)
 * are left out to be added later.
 */
public class Create_View extends Application {

    private static final Logger LOGGER = Logger.getLogger(Create_View.class.getName());

    @Override
    public void start(Stage primaryStage) {
        TextField nameField = new TextField();
        TextField emailField = new TextField();
        PasswordField passwordField = new PasswordField();
        Label createStatus = new Label();

        HBox headerBox = buildHeader();
        HBox nameBox = buildNameRow(nameField);
        HBox emailBox = buildEmailRow(emailField);
        HBox passwordBox = buildPasswordRow(passwordField);

        Database database = openDatabase(createStatus);
        Registration registration = new Registration(database);
        CreateAccountController controller = new CreateAccountController(
                registration, nameField, emailField, passwordField, createStatus
        );

        HBox createAccountBox = buildCreateAccountBox(
                controller, nameField, emailField, passwordField, primaryStage);
        VBox userBox = buildUserBox(headerBox, nameBox, emailBox, passwordBox, createAccountBox, createStatus);
        HBox root = buildRoot(userBox);

        primaryStage.setTitle("Create Account");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /** Welcome header shown above the form. */
    private HBox buildHeader() {
        Label welcomeLabel = new Label("Welcome to Hello Application!");
        HBox headerBox = new HBox(welcomeLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPrefSize(200, 50);
        headerBox.setPadding(new Insets(0, 0, 0, 24));
        return headerBox;
    }

    /** Name label + text field. */
    private HBox buildNameRow(TextField nameField) {
        Label nameLabel = new Label("Name");
        nameField.setPromptText("First Last");
        HBox nameBox = new HBox(10, nameLabel, nameField);
        nameBox.setAlignment(Pos.CENTER_RIGHT);
        return nameBox;
    }

    /** Email label + text field. */
    private HBox buildEmailRow(TextField emailField) {
        Label emailLabel = new Label("Email");
        HBox emailBox = new HBox(10, emailLabel, emailField);
        emailBox.setAlignment(Pos.CENTER_RIGHT);
        return emailBox;
    }

    /** Password label + masked field. */
    private HBox buildPasswordRow(PasswordField passwordField) {
        Label passwordLabel = new Label("Password");
        HBox passwordBox = new HBox(10, passwordLabel, passwordField);
        passwordBox.setAlignment(Pos.CENTER_RIGHT);
        return passwordBox;
    }

    /** Opens (and initializes) the SQLite-backed Database, reporting failure via the status label. */
    private Database openDatabase(Label createStatus) {
        Path dbFile = Path.of("data", "users.db");
        Database database = new Database(dbFile);
        try {
            database.init();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize database", e);
            createStatus.setText("Could not reach the database.");
        }
        return database;
    }

    /**
     * Create Account button, wired to trigger on click or Enter in any field.
     * On success, shows the status message briefly, then hands off to Login_View.
     */
    private HBox buildCreateAccountBox(CreateAccountController controller, TextField nameField,
                                        TextField emailField, PasswordField passwordField,
                                        Stage primaryStage) {
        Button createAccountBtn = new Button("Create Account");

        Runnable submit = () -> {
            Registration.Status result = controller.handleCreateAccount();
            if (result == Registration.Status.OK) {
                PauseTransition delay = new PauseTransition(Duration.seconds(1.2));
                delay.setOnFinished(e -> {
                    primaryStage.close();
                    new Login_View().start(new Stage());
                });
                delay.play();
            }
        };
        // Trigger account creation on button click or Enter in any field
        createAccountBtn.setOnAction(e -> submit.run());
        nameField.setOnAction(e -> submit.run());
        emailField.setOnAction(e -> submit.run());
        passwordField.setOnAction(e -> submit.run());

        HBox createAccountBox = new HBox(createAccountBtn);
        createAccountBox.setAlignment(Pos.CENTER);
        createAccountBox.setPadding(new Insets(24, 0, 0, 24));
        return createAccountBox;
    }

    /** Stacks all rows into the form; matches the FXML's UserBox VBox. */
    private VBox buildUserBox(HBox headerBox, HBox nameBox, HBox emailBox, HBox passwordBox,
                               HBox createAccountBox, Label createStatus) {
        VBox userBox = new VBox(24, headerBox, nameBox, emailBox, passwordBox, createAccountBox, createStatus);
        userBox.setAlignment(Pos.CENTER);
        userBox.setPadding(new Insets(0, 24, 0, 0));
        return userBox;
    }

    /** Centers the form; matches the FXML's 400x400 HBox root. */
    private HBox buildRoot(VBox userBox) {
        HBox root = new HBox(userBox);
        root.setAlignment(Pos.CENTER);
        root.setPrefSize(400, 400);
        return root;
    }
}

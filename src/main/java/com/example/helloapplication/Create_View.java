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

/**
 * Native JavaFX create-account window (no FXML).
 * Mirrors the layout of Documents/FXML/Create-View.fxml: a centered form with
 * name, email, and password fields, then a Create Account button.
 * Account creation is delegated to {@link CreateAccountController} using
 * {@link Registration}. Per-field icons from the FXML (Gluon Glisten Icon)
 * are left out to be added later.
 */
public class Create_View extends Application {

    @Override
    public void start(Stage primaryStage) {
        // --- Header ---
        Label welcomeLabel = new Label("Welcome to Hello Application!");
        HBox headerBox = new HBox(welcomeLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPrefSize(200, 50);
        headerBox.setPadding(new Insets(0, 0, 0, 24));

        // --- Name row ---
        Label nameLabel = new Label("Name");
        TextField nameField = new TextField();
        nameField.setPromptText("First Last");
        HBox nameBox = new HBox(10, nameLabel, nameField);
        nameBox.setAlignment(Pos.CENTER_RIGHT);

        // --- Email row ---
        Label emailLabel = new Label("Email");
        TextField emailField = new TextField();
        HBox emailBox = new HBox(10, emailLabel, emailField);
        emailBox.setAlignment(Pos.CENTER_RIGHT);

        // --- Password row ---
        Label passwordLabel = new Label("Password");
        PasswordField passwordField = new PasswordField();
        HBox passwordBox = new HBox(10, passwordLabel, passwordField);
        passwordBox.setAlignment(Pos.CENTER_RIGHT);

        // --- Create Account button ---
        Button createAccountBtn = new Button("Create Account");
        HBox createAccountBox = new HBox(createAccountBtn);
        createAccountBox.setAlignment(Pos.CENTER);
        createAccountBox.setPadding(new Insets(24, 0, 0, 24));

        // --- Feedback label: updated by CreateAccountController after each attempt ---
        Label createStatus = new Label();

        Path dbFile = Path.of("data", "users.db");
        Database database = new Database(dbFile);
        try {
            database.init();
        } catch (SQLException e) {
            e.printStackTrace();
            createStatus.setText("Could not reach the database.");
        }

        Registration registration = new Registration(database);
        CreateAccountController controller = new CreateAccountController(
                registration, nameField, emailField, passwordField, createStatus
        );
        // On success, show the status message briefly, then hand off to Login_View.
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

        // --- Form: stacks all rows, matches the FXML's UserBox VBox ---
        VBox userBox = new VBox(24, headerBox, nameBox, emailBox, passwordBox, createAccountBox, createStatus);
        userBox.setAlignment(Pos.CENTER);
        userBox.setPadding(new Insets(0, 24, 0, 0));

        // --- Root layout: centers the form, matches the FXML's 400x400 HBox root ---
        HBox root = new HBox(userBox);
        root.setAlignment(Pos.CENTER);
        root.setPrefSize(400, 400);

        primaryStage.setTitle("Create Account");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }
}

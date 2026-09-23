package com.example.helloapplication;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Create_View {

    private static final Logger LOGGER = Logger.getLogger(Create_View.class.getName());

    public void show(Stage primaryStage) {
        TextField nameField = new TextField();
        TextField emailField = new TextField();
        PasswordField passwordField = new PasswordField();
        PasswordVisibilityToggle passwordToggle = PasswordVisibilityToggle.wrap(passwordField);
        Label createStatus = new Label();
        createStatus.setWrapText(true);
        createStatus.setMaxWidth(320);
        StatusLabelAlignment.applyTo(createStatus);

        HBox headerBox = buildHeader();
        HBox nameBox = buildNameRow(nameField);
        HBox emailBox = buildEmailRow(emailField);
        HBox passwordBox = buildPasswordRow(passwordToggle.field());

        Database database = openDatabase(createStatus);
        Registration registration = new Registration(database);
        CreateAccountController controller = new CreateAccountController(
                registration, nameField, emailField, passwordField, createStatus
        );

        HBox createAccountBox = buildCreateAccountBox(
                controller, nameField, emailField, passwordField, passwordToggle.toggleButton(), primaryStage);
        VBox userBox = buildUserBox(headerBox, nameBox, emailBox, passwordBox, createAccountBox, createStatus);
        HBox root = buildRoot(userBox);

        primaryStage.setTitle("Create Account");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    private HBox buildHeader() {
        Label welcomeLabel = new Label("Welcome to Hello Application!");
        HBox headerBox = new HBox(welcomeLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPrefSize(200, 50);
        headerBox.setPadding(new Insets(0, 0, 0, 24));
        return headerBox;
    }

    private HBox buildNameRow(TextField nameField) {
        Label nameLabel = new Label("Name");
        nameField.setPromptText("First Last");
        HBox nameBox = new HBox(10, nameLabel, nameField);
        nameBox.setAlignment(Pos.CENTER_RIGHT);
        return nameBox;
    }

    private HBox buildEmailRow(TextField emailField) {
        Label emailLabel = new Label("Email");
        HBox emailBox = new HBox(10, emailLabel, emailField);
        emailBox.setAlignment(Pos.CENTER_RIGHT);
        return emailBox;
    }

    private HBox buildPasswordRow(StackPane passwordField) {
        Label passwordLabel = new Label("Password");
        HBox passwordBox = new HBox(10, passwordLabel, passwordField);
        passwordBox.setAlignment(Pos.CENTER_RIGHT);
        return passwordBox;
    }

    private Database openDatabase(Label createStatus) {
        try {
            return Database.users();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize " + Database.USERS_FILE.toAbsolutePath(), e);
            createStatus.setText("Could not reach the database.");
            return new Database(Database.USERS_FILE);
        }
    }

    private HBox buildCreateAccountBox(CreateAccountController controller, TextField nameField,
                                        TextField emailField, PasswordField passwordField,
                                        ToggleButton showPasswordToggle, Stage primaryStage) {
        Button createAccountBtn = new Button("Create Account");

        Runnable submit = () -> {
            Registration.Status result = controller.handleCreateAccount();
            if (result == Registration.Status.OK) {
                PauseTransition delay = new PauseTransition(Duration.seconds(1.2));
                delay.setOnFinished(e -> new Login_View().show(primaryStage));
                delay.play();
            }
        };
        // Trigger account creation on button click or Enter in any field
        createAccountBtn.setOnAction(e -> submit.run());
        nameField.setOnAction(e -> submit.run());
        emailField.setOnAction(e -> submit.run());
        passwordField.setOnAction(e -> submit.run());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> Views.navigate("Login_View", () -> new Login_View().show(primaryStage)));

        HBox createAccountBox = new HBox(10, createAccountBtn, cancelBtn, showPasswordToggle);
        createAccountBox.setAlignment(Pos.CENTER);
        createAccountBox.setPadding(new Insets(24, 0, 0, 24));
        return createAccountBox;
    }

    private VBox buildUserBox(HBox headerBox, HBox nameBox, HBox emailBox, HBox passwordBox,
                               HBox createAccountBox, Label createStatus) {
        VBox userBox = new VBox(24, headerBox, nameBox, emailBox, passwordBox, createAccountBox, createStatus);
        userBox.setAlignment(Pos.CENTER);
        userBox.setPadding(new Insets(0, 24, 0, 0));
        return userBox;
    }

    private HBox buildRoot(VBox userBox) {
        HBox root = new HBox(userBox);
        root.setAlignment(Pos.CENTER);
        root.setPrefSize(400, 400);
        return root;
    }
}

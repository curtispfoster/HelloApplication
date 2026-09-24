package com.example.helloapplication;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Account creation, styled like Login_View and ChangePassword_View: a
 * blueprint side panel drawing the Users table, and a record-grid form named
 * after the real columns it fills in (Name, Username, Password). The email
 * entered is stored as Username, matching how Login_View accepts either.
 */
public class Create_View {

    private static final Logger LOGGER = Logger.getLogger(Create_View.class.getName());
    private static final PseudoClass PLACEHOLDER = PseudoClass.getPseudoClass("placeholder");

    public Create_View() {
    }

    public void show(Stage primaryStage) {
        TextField nameField = new TextField();
        TextField emailField = new TextField();
        emailField.setPromptText("you@example.com");
        PasswordField passwordField = new PasswordField();
        passwordField.setMaxWidth(Double.MAX_VALUE);
        PasswordVisibilityToggle passwordToggle = PasswordVisibilityToggle.wrap(passwordField);

        Label createStatus = new Label("");
        createStatus.getStyleClass().add("status-bar");
        createStatus.setWrapText(true);
        createStatus.setMaxWidth(Double.MAX_VALUE);

        Database database = openDatabase(createStatus);
        Registration registration = new Registration(database);
        CreateAccountController controller = new CreateAccountController(
                registration, nameField, emailField, passwordField, createStatus);

        Pane diagram = SchemaDiagram.build();
        SchemaDiagram.highlightWhileFocused(diagram, "Name", nameField.focusedProperty());
        SchemaDiagram.highlightWhileFocused(diagram, "Username", emailField.focusedProperty());
        SchemaDiagram.highlightWhileFocused(diagram, "Password", passwordToggle.field().focusWithinProperty());

        VBox fieldsBlock = new VBox(8,
                buildFieldsGrid(nameField, emailField, passwordToggle),
                buildPolicyHint());

        VBox form = new VBox(22,
                buildHeading(emailField),
                fieldsBlock,
                buildActionsRow(controller, nameField, emailField, passwordField, createStatus, primaryStage));
        form.getStyleClass().add("login-content");
        form.setAlignment(Pos.CENTER_LEFT);

        BorderPane formPane = new BorderPane(form);
        formPane.setBottom(createStatus);
        formPane.setPrefWidth(430);
        HBox.setHgrow(formPane, Priority.ALWAYS);

        HBox root = new HBox(SchemaDiagram.sidePanel(diagram), formPane);
        root.setPrefHeight(480);

        Scene scene = new Scene(root);
        Views.addStylesheets(scene, "theme.css", "login.css");

        primaryStage.setTitle("Create account");
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.show();
        nameField.requestFocus();
    }

    private VBox buildHeading(TextField emailField) {
        Label user = new Label();
        user.getStyleClass().add("conn-user");
        user.setMinWidth(0);
        user.textProperty().bind(Bindings.when(emailField.textProperty().isEmpty())
                .then("username").otherwise(emailField.textProperty()));
        emailField.textProperty().addListener((obs, was, now) ->
                user.pseudoClassStateChanged(PLACEHOLDER, now.isEmpty()));
        user.pseudoClassStateChanged(PLACEHOLDER, true);

        Region caret = new Region();
        caret.getStyleClass().add("conn-caret");
        caret.visibleProperty().bind(emailField.focusedProperty());
        caret.managedProperty().bind(caret.visibleProperty());
        HBox.setMargin(caret, new Insets(0, 3, 0, 2));

        Label host = new Label("@users.db");
        host.getStyleClass().add("conn-host");
        host.setMinWidth(Region.USE_PREF_SIZE);

        HBox connectionString = new HBox(user, caret, host);
        connectionString.setAlignment(Pos.CENTER_LEFT);

        Label prompt = new Label("Create an account");
        prompt.getStyleClass().add("login-muted");
        return new VBox(4, connectionString, prompt);
    }

    private GridPane buildFieldsGrid(TextField nameField, TextField emailField,
                                      PasswordVisibilityToggle passwordToggle) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("record-grid");
        ColumnConstraints keyColumn = new ColumnConstraints(96);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyColumn, valueColumn);

        nameField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        emailField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(emailField, Priority.ALWAYS);
        StackPane passwordStack = passwordToggle.field();
        HBox.setHgrow(passwordStack, Priority.ALWAYS);

        grid.add(Views.recordKey("name", "record-row-first"), 0, 0);
        grid.add(Views.recordValue("record-row-first", nameField), 1, 0);
        grid.add(Views.recordKey("username", "record-row-middle"), 0, 1);
        grid.add(Views.recordValue("record-row-middle", emailField), 1, 1);
        grid.add(Views.recordKey("password", "record-row-last"), 0, 2);
        grid.add(Views.recordValue("record-row-last", passwordStack, passwordToggle.toggleButton()), 1, 2);
        return grid;
    }

    private Label buildPolicyHint() {
        Label hint = new Label("At least 8 characters, with a number and a symbol.");
        hint.getStyleClass().addAll("login-muted", "field-hint");
        return hint;
    }

    private Database openDatabase(Label createStatus) {
        try {
            return Database.users();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize " + Database.USERS_FILE.toAbsolutePath(), e);
            Views.setStatus(createStatus, "Could not reach the database.", "status-error");
            return new Database(Database.USERS_FILE);
        }
    }

    private HBox buildActionsRow(CreateAccountController controller, TextField nameField, TextField emailField,
                                  PasswordField passwordField, Label createStatus, Stage primaryStage) {
        Button createAccountBtn = new Button("Create account");
        createAccountBtn.getStyleClass().add("primary-button");

        Runnable submit = () -> {
            Registration.Status result = controller.handleCreateAccount();
            Views.setStatus(createStatus, createStatus.getText(),
                    result == Registration.Status.OK ? "status-ok" : "status-error");
            if (result == Registration.Status.OK) {
                Views.navigate("Login_View", () -> new Login_View().show(primaryStage));
            }
        };
        createAccountBtn.setOnAction(e -> submit.run());
        nameField.setOnAction(e -> submit.run());
        emailField.setOnAction(e -> submit.run());
        passwordField.setOnAction(e -> submit.run());

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> Views.navigate("Login_View", () -> new Login_View().show(primaryStage)));

        HBox actions = new HBox(10, createAccountBtn, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);
        return actions;
    }
}

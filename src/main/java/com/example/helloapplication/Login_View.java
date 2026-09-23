package com.example.helloapplication;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
//To-do list: see "TODO.md" in documentation directory.
//Refer to "Adding Database README.md" in documentation directory for schema details.
//When completed or stopped for the day update "Coding Journal.md"

public class Login_View {

    private static final Logger LOGGER = Logger.getLogger(Login_View.class.getName());

    private static final PseudoClass PLACEHOLDER = PseudoClass.getPseudoClass("placeholder");

    public void show(Stage primaryStage) {
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        // Unbounded before wrapping so the plain-text mirror copies it too.
        passwordField.setMaxWidth(Double.MAX_VALUE);
        PasswordVisibilityToggle passwordToggle = PasswordVisibilityToggle.wrap(passwordField);
        Label loginStatus = new Label("");
        loginStatus.getStyleClass().add("status-bar");
        loginStatus.setWrapText(true);
        loginStatus.setMaxWidth(Double.MAX_VALUE);

        Database database = openDatabase(loginStatus);
        Authenticator auth = new Authenticator(database);
        LoginController controller = new LoginController(auth, usernameField, passwordField, loginStatus);
        if (loginStatus.getText().isEmpty()) {
            loginStatus.setText("Not connected");
        }

        Button loginBtn = buildLoginButton(controller, usernameField, passwordField, loginStatus, primaryStage);

        Pane diagram = SchemaDiagram.build();
        SchemaDiagram.highlightWhileFocused(diagram, "Username", usernameField.focusedProperty());
        SchemaDiagram.highlightWhileFocused(diagram, "Password", passwordToggle.field().focusWithinProperty());

        VBox form = new VBox(22,
                buildHeading(usernameField),
                buildCredentialsGrid(usernameField, passwordToggle),
                buildActionsRow(loginBtn),
                buildCreateAccountRow(primaryStage));
        form.getStyleClass().add("login-content");
        form.setAlignment(Pos.CENTER_LEFT);

        BorderPane formPane = new BorderPane(form);
        formPane.setBottom(loginStatus);
        formPane.setPrefWidth(430);
        HBox.setHgrow(formPane, Priority.ALWAYS);

        HBox root = new HBox(SchemaDiagram.sidePanel(diagram), formPane);
        root.setPrefHeight(440);

        Scene scene = new Scene(root);
        Views.addStylesheets(scene, "theme.css", "login.css");

        primaryStage.setTitle("Sign in");
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.show();
    }

    private VBox buildHeading(TextField usernameField) {
        Label user = new Label();
        user.getStyleClass().add("conn-user");
        user.setMinWidth(0);
        user.textProperty().bind(Bindings.when(usernameField.textProperty().isEmpty())
                .then("username").otherwise(usernameField.textProperty()));
        usernameField.textProperty().addListener((obs, was, now) ->
                user.pseudoClassStateChanged(PLACEHOLDER, now.isEmpty()));
        user.pseudoClassStateChanged(PLACEHOLDER, true);

        Region caret = new Region();
        caret.getStyleClass().add("conn-caret");
        caret.visibleProperty().bind(usernameField.focusedProperty());
        caret.managedProperty().bind(caret.visibleProperty());
        HBox.setMargin(caret, new Insets(0, 3, 0, 2));

        Label host = new Label("@users.db");
        host.getStyleClass().add("conn-host");
        host.setMinWidth(Region.USE_PREF_SIZE);

        HBox connectionString = new HBox(user, caret, host);
        connectionString.setAlignment(Pos.CENTER_LEFT);

        Label prompt = new Label("Sign in to connect");
        prompt.getStyleClass().add("login-muted");
        return new VBox(4, connectionString, prompt);
    }

    private GridPane buildCredentialsGrid(TextField usernameField, PasswordVisibilityToggle passwordToggle) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("record-grid");
        ColumnConstraints keyColumn = new ColumnConstraints(96);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyColumn, valueColumn);

        usernameField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(usernameField, Priority.ALWAYS);
        StackPane passwordStack = passwordToggle.field();
        HBox.setHgrow(passwordStack, Priority.ALWAYS);

        grid.add(Views.recordKey("username", "record-row-first"), 0, 0);
        grid.add(Views.recordValue("record-row-first", usernameField), 1, 0);
        grid.add(Views.recordKey("password", "record-row-last"), 0, 1);
        grid.add(Views.recordValue("record-row-last", passwordStack, passwordToggle.toggleButton()), 1, 1);
        return grid;
    }

    private HBox buildActionsRow(Button loginBtn) {
        Hyperlink forgotLink = new Hyperlink("Forgot password?");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(loginBtn, spacer, forgotLink);
        actions.setAlignment(Pos.CENTER_LEFT);
        return actions;
    }

    private HBox buildCreateAccountRow(Stage primaryStage) {
        Label newHere = new Label("New here?");
        newHere.getStyleClass().add("login-muted");
        Hyperlink createAccountLink = new Hyperlink("Create an account");
        createAccountLink.setOnAction(e -> Views.navigate("Create_View", () -> new Create_View().show(primaryStage)));
        HBox row = new HBox(4, newHere, createAccountLink);
        row.setAlignment(Pos.BASELINE_LEFT);
        return row;
    }

    private Database openDatabase(Label loginStatus) {
        try {
            return Database.users();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize " + Database.USERS_FILE.toAbsolutePath(), e);
            Views.setStatus(loginStatus, "Could not reach the database.", "status-error");
            return new Database(Database.USERS_FILE);
        }
    }

    private Button buildLoginButton(LoginController controller, TextField usernameField,
                                     PasswordField passwordField, Label loginStatus, Stage primaryStage) {
        Button loginBtn = new Button("Connect");
        loginBtn.getStyleClass().add("primary-button");

        Runnable attempt = () -> attemptLogin(controller, loginStatus, primaryStage);
        loginBtn.setOnAction(e -> attempt.run());
        usernameField.setOnAction(e -> attempt.run());
        passwordField.setOnAction(e -> attempt.run());

        return loginBtn;
    }

    private void attemptLogin(LoginController controller, Label loginStatus, Stage primaryStage) {
        Roles result = controller.handleLogin();
        Views.setStatus(loginStatus, loginStatus.getText(),
                result.status == Authenticator.Status.OK ? "status-ok" : "status-error");
        routeAfterLogin(result, primaryStage);
    }

    private void routeAfterLogin(Roles result, Stage primaryStage) {
        if (result.status != Authenticator.Status.OK) {
            return;
        }

        if (result.mustChangePassword) {
            Views.navigate("ChangePassword_View", () -> new ChangePassword_View(result, true).show(primaryStage));
        } else {
            Views.openLandingView(primaryStage, result);
        }
    }
}

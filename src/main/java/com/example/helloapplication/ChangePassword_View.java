package com.example.helloapplication;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ToggleButton;
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

public class ChangePassword_View {

    private static final Logger LOGGER = Logger.getLogger(ChangePassword_View.class.getName());

    private final Roles actor;
    private final boolean forced;

    public ChangePassword_View(Roles actor, boolean forced) {
        this.actor = actor;
        this.forced = forced;
    }

    public void show(Stage primaryStage) {
        PasswordField newPasswordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        // Unbounded before wrapping so the plain-text mirrors copy it too.
        newPasswordField.setMaxWidth(Double.MAX_VALUE);
        confirmPasswordField.setMaxWidth(Double.MAX_VALUE);
        Label status = new Label("Connected");
        status.getStyleClass().add("status-bar");
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);

        // One shared toggle for both fields, so the user can reveal them
        // together and visually confirm they match.
        ToggleButton showToggle = new ToggleButton("Show");
        showToggle.selectedProperty().addListener(
                (obs, wasShowing, showing) -> showToggle.setText(showing ? "Hide" : "Show"));
        showToggle.setMinWidth(Region.USE_PREF_SIZE);
        StackPane newPasswordStack = PasswordVisibilityToggle.wrap(newPasswordField, showToggle);
        StackPane confirmPasswordStack = PasswordVisibilityToggle.wrap(confirmPasswordField, showToggle);

        Database database = openDatabase(status);
        PasswordChange passwordChange = new PasswordChange(database);
        ChangePasswordController controller = new ChangePasswordController(
                passwordChange, actor.username, newPasswordField, confirmPasswordField, status);

        Pane diagram = SchemaDiagram.build();
        SchemaDiagram.highlightWhileFocused(diagram, "Password", newPasswordStack.focusWithinProperty());
        SchemaDiagram.highlightWhileFocused(diagram, "Password", confirmPasswordStack.focusWithinProperty());

        VBox passwordBlock = new VBox(8,
                buildPasswordGrid(newPasswordStack, confirmPasswordStack, showToggle),
                buildPolicyHint());

        VBox form = new VBox(22,
                buildHeading(),
                passwordBlock,
                buildActionsRow(controller, newPasswordField, confirmPasswordField, status, primaryStage));
        form.getStyleClass().add("login-content");
        form.setAlignment(Pos.CENTER_LEFT);

        BorderPane formPane = new BorderPane(form);
        formPane.setBottom(status);
        formPane.setPrefWidth(430);
        HBox.setHgrow(formPane, Priority.ALWAYS);

        HBox root = new HBox(SchemaDiagram.sidePanel(diagram), formPane);
        root.setPrefHeight(440);

        Scene scene = new Scene(root);
        Views.addStylesheets(scene, "theme.css", "login.css");

        primaryStage.setTitle("Change password");
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.show();
        newPasswordField.requestFocus();
    }

    private VBox buildHeading() {
        Label user = new Label(actor.username);
        user.getStyleClass().add("conn-user");
        user.setMinWidth(0);

        Label host = new Label("@users.db");
        host.getStyleClass().add("conn-host");
        host.setMinWidth(Region.USE_PREF_SIZE);

        HBox connectionString = new HBox(user, host);
        connectionString.setAlignment(Pos.CENTER_LEFT);

        Label prompt = new Label(forced ? "Set a new password to continue" : "Choose a new password");
        prompt.getStyleClass().add("login-muted");
        return new VBox(4, connectionString, prompt);
    }

    private GridPane buildPasswordGrid(StackPane newPasswordStack, StackPane confirmPasswordStack,
                                       ToggleButton showToggle) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("record-grid");
        ColumnConstraints keyColumn = new ColumnConstraints(124);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyColumn, valueColumn);

        HBox.setHgrow(newPasswordStack, Priority.ALWAYS);
        HBox.setHgrow(confirmPasswordStack, Priority.ALWAYS);

        grid.add(Views.recordKey("new password", "record-row-first"), 0, 0);
        grid.add(Views.recordValue("record-row-first", newPasswordStack, showToggle), 1, 0);
        grid.add(Views.recordKey("confirm", "record-row-last"), 0, 1);
        grid.add(Views.recordValue("record-row-last", confirmPasswordStack), 1, 1);
        return grid;
    }

    private Label buildPolicyHint() {
        Label hint = new Label("At least 8 characters, with a number and a symbol.");
        hint.getStyleClass().addAll("login-muted", "field-hint");
        return hint;
    }

    private Database openDatabase(Label status) {
        try {
            return Database.users();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize " + Database.USERS_FILE.toAbsolutePath(), e);
            Views.setStatus(status, "Could not reach the database.", "status-error");
            return new Database(Database.USERS_FILE);
        }
    }

    private HBox buildActionsRow(ChangePasswordController controller, PasswordField newPasswordField,
                                 PasswordField confirmPasswordField, Label status, Stage primaryStage) {
        Button submit = new Button("Update password");
        submit.getStyleClass().add("primary-button");
        Runnable submitAction = () -> {
            boolean changed = controller.handleChangePassword();
            Views.setStatus(status, status.getText(), changed ? "status-ok" : "status-error");
            if (changed) {
                advance(primaryStage);
            }
        };
        submit.setOnAction(e -> submitAction.run());
        newPasswordField.setOnAction(e -> submitAction.run());
        confirmPasswordField.setOnAction(e -> submitAction.run());

        HBox actions = new HBox(10, submit);
        if (!forced) {
            Button cancel = new Button("Cancel");
            cancel.getStyleClass().add("secondary-button");
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> advance(primaryStage));
            actions.getChildren().add(cancel);
        }
        actions.setAlignment(Pos.CENTER_LEFT);
        return actions;
    }

    private void advance(Stage primaryStage) {
        Views.openLandingView(primaryStage, actor);
    }
}

package com.example.helloapplication;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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

/** Shown instead of Login_View until the first OWNER account exists. */
public class OwnerSetup_View {

    private static final Logger LOGGER = Logger.getLogger(OwnerSetup_View.class.getName());

    public OwnerSetup_View() {
    }

    public void show(Stage primaryStage) {
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        usernameField.setMaxWidth(Double.MAX_VALUE);
        // Unbounded before wrapping so the plain-text mirrors copy it too.
        passwordField.setMaxWidth(Double.MAX_VALUE);
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
        StackPane passwordStack = PasswordVisibilityToggle.wrap(passwordField, showToggle);
        StackPane confirmPasswordStack = PasswordVisibilityToggle.wrap(confirmPasswordField, showToggle);

        Database database = openDatabase(status);
        OwnerSetupController controller = new OwnerSetupController(
                new OwnerSetup(database), usernameField, passwordField, confirmPasswordField, status);

        Pane diagram = SchemaDiagram.build();
        SchemaDiagram.highlightWhileFocused(diagram, "Username", usernameField.focusedProperty());
        SchemaDiagram.highlightWhileFocused(diagram, "Password", passwordStack.focusWithinProperty());
        SchemaDiagram.highlightWhileFocused(diagram, "Password", confirmPasswordStack.focusWithinProperty());

        VBox credentialsBlock = new VBox(8,
                buildCredentialsGrid(usernameField, passwordStack, confirmPasswordStack, showToggle),
                buildPolicyHint());

        VBox form = new VBox(22,
                buildHeading(),
                credentialsBlock,
                buildActionsRow(controller, usernameField, passwordField, confirmPasswordField, status,
                        primaryStage));
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

        primaryStage.setTitle("Set up");
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.show();
        usernameField.requestFocus();
    }

    private VBox buildHeading() {
        Label user = new Label("owner");
        user.getStyleClass().add("conn-user");
        user.setMinWidth(0);

        Label host = new Label("@users.db");
        host.getStyleClass().add("conn-host");
        host.setMinWidth(Region.USE_PREF_SIZE);

        HBox connectionString = new HBox(user, host);
        connectionString.setAlignment(Pos.CENTER_LEFT);

        Label prompt = new Label("Create the owner account to finish setup");
        prompt.getStyleClass().add("login-muted");
        return new VBox(4, connectionString, prompt);
    }

    private GridPane buildCredentialsGrid(TextField usernameField, StackPane passwordStack,
                                          StackPane confirmPasswordStack, ToggleButton showToggle) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("record-grid");
        ColumnConstraints keyColumn = new ColumnConstraints(124);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyColumn, valueColumn);

        HBox.setHgrow(usernameField, Priority.ALWAYS);
        HBox.setHgrow(passwordStack, Priority.ALWAYS);
        HBox.setHgrow(confirmPasswordStack, Priority.ALWAYS);

        grid.add(Views.recordKey("username", "record-row-first"), 0, 0);
        grid.add(Views.recordValue("record-row-first", usernameField), 1, 0);
        grid.add(Views.recordKey("password", "record-row-middle"), 0, 1);
        grid.add(Views.recordValue("record-row-middle", passwordStack, showToggle), 1, 1);
        grid.add(Views.recordKey("confirm", "record-row-last"), 0, 2);
        grid.add(Views.recordValue("record-row-last", confirmPasswordStack), 1, 2);
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

    private HBox buildActionsRow(OwnerSetupController controller, TextField usernameField,
                                 PasswordField passwordField, PasswordField confirmPasswordField,
                                 Label status, Stage primaryStage) {
        Button submit = new Button("Create owner");
        submit.getStyleClass().add("primary-button");
        Runnable submitAction = () -> {
            boolean finished = controller.handleCreateOwner();
            Views.setStatus(status, status.getText(), finished ? "status-ok" : "status-error");
            if (finished) {
                Views.navigate("Login_View", () -> new Login_View().show(primaryStage));
            }
        };
        submit.setOnAction(e -> submitAction.run());
        usernameField.setOnAction(e -> submitAction.run());
        passwordField.setOnAction(e -> submitAction.run());
        confirmPasswordField.setOnAction(e -> submitAction.run());

        HBox actions = new HBox(10, submit);
        actions.setAlignment(Pos.CENTER_LEFT);
        return actions;
    }
}

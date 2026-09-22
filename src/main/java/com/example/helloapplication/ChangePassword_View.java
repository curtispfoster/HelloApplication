package com.example.helloapplication;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Native JavaFX change-password window (no FXML).
 * Used two ways: forced (no Cancel button) right after logging in with a
 * seeded default password, and as an optional self-service screen opened
 * from Home_View/Admin_View, where Cancel returns without changing
 * anything.
 */
public class ChangePassword_View {

    private static final Logger LOGGER = Logger.getLogger(ChangePassword_View.class.getName());

    private final String username;
    private final boolean isAdmin;
    private final boolean forced;

    public ChangePassword_View(String username, boolean isAdmin, boolean forced) {
        this.username = username;
        this.isAdmin = isAdmin;
        this.forced = forced;
    }

    public void show(Stage primaryStage) {
        PasswordField newPasswordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        Label status = new Label(forced ? "Set a new password to continue." : "");
        status.setWrapText(true);
        status.setMaxWidth(320);
        StatusLabelAlignment.applyTo(status);

        // One shared toggle for both fields, so the user can reveal them
        // together and visually confirm they match.
        ToggleButton showToggle = new ToggleButton("Show");
        showToggle.selectedProperty().addListener(
                (obs, wasShowing, showing) -> showToggle.setText(showing ? "Hide" : "Show"));

        VBox newPasswordBox = buildPasswordRow("New password", newPasswordField, showToggle);
        VBox confirmPasswordBox = buildPasswordRow("Confirm password", confirmPasswordField, showToggle);

        Database database = openDatabase(status);
        PasswordChange passwordChange = new PasswordChange(database);
        ChangePasswordController controller = new ChangePasswordController(
                passwordChange, username, newPasswordField, confirmPasswordField, status);

        HBox buttonBox = buildButtonBox(
                controller, newPasswordField, confirmPasswordField, showToggle, primaryStage);
        VBox root = buildRoot(newPasswordBox, confirmPasswordBox, buttonBox, status);

        primaryStage.setTitle("Change Password");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /** Label + masked field, shared shape for both password rows; visibility is driven by the shared toggle. */
    private VBox buildPasswordRow(String labelText, PasswordField field, ToggleButton showToggle) {
        Label label = new Label(labelText);
        field.setMaxWidth(200);
        StackPane fieldStack = PasswordVisibilityToggle.wrap(field, showToggle);

        VBox box = new VBox(6, label, fieldStack);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** Opens (and initializes) the SQLite-backed Database, reporting failure via the status label. */
    private Database openDatabase(Label status) {
        Path dbFile = Path.of("data", "users.db");
        Database database = new Database(dbFile);
        try {
            database.init();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize database", e);
            status.setText("Could not reach the database.");
        }
        return database;
    }

    /**
     * Submit button (and Cancel, unless this change is forced) plus the
     * shared show/hide toggle placed to its right, wired to click or Enter
     * in either field.
     */
    private HBox buildButtonBox(ChangePasswordController controller, PasswordField newPasswordField,
                                 PasswordField confirmPasswordField, ToggleButton showToggle, Stage primaryStage) {
        Button submit = new Button("Submit");
        Runnable submitAction = () -> {
            if (controller.handleChangePassword()) {
                advance(primaryStage);
            }
        };
        submit.setOnAction(e -> submitAction.run());
        newPasswordField.setOnAction(e -> submitAction.run());
        confirmPasswordField.setOnAction(e -> submitAction.run());

        HBox buttonBox;
        if (forced) {
            buttonBox = new HBox(10, submit, showToggle);
        } else {
            Button cancel = new Button("Cancel");
            cancel.setOnAction(e -> advance(primaryStage));
            buttonBox = new HBox(10, submit, showToggle, cancel);
        }
        buttonBox.setAlignment(Pos.CENTER);
        return buttonBox;
    }

    /** Swaps this window over to Admin_View or Home_View depending on role. */
    private void advance(Stage primaryStage) {
        try {
            if (isAdmin) {
                new Admin_View(username).show(primaryStage);
            } else {
                new Home_View(username).show(primaryStage);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not open next view after password change", ex);
        }
    }

    private VBox buildRoot(VBox newPasswordBox, VBox confirmPasswordBox, HBox buttonBox, Label status) {
        VBox root = new VBox(12, newPasswordBox, confirmPasswordBox, buttonBox, status);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setPrefWidth(400);
        root.setPrefHeight(320);
        return root;
    }
}

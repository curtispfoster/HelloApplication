package com.example.helloapplication;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * Pairs a masked PasswordField with a plain-text mirror and a Show/Hide
 * ToggleButton. The two fields share their text via a bidirectional
 * binding, so callers keep wiring the original PasswordField passed in —
 * its text is always current regardless of which one is visible. Set any
 * sizing (e.g. setMaxWidth) on the PasswordField before calling wrap().
 * The field and the toggle button are exposed separately so callers can
 * lay them out together (see {@link #asRow()}) or place the toggle
 * elsewhere on the screen.
 */
public record PasswordVisibilityToggle(StackPane field, ToggleButton toggleButton) {

    public static PasswordVisibilityToggle wrap(PasswordField passwordField) {
        TextField plainField = new TextField();
        plainField.textProperty().bindBidirectional(passwordField.textProperty());
        plainField.setMaxWidth(passwordField.getMaxWidth());
        plainField.setManaged(false);
        plainField.setVisible(false);

        StackPane fieldStack = new StackPane(passwordField, plainField);

        ToggleButton toggle = new ToggleButton("Show");
        toggle.selectedProperty().addListener((obs, wasShowing, showing) -> {
            passwordField.setVisible(!showing);
            passwordField.setManaged(!showing);
            plainField.setVisible(showing);
            plainField.setManaged(showing);
            toggle.setText(showing ? "Hide" : "Show");
        });

        return new PasswordVisibilityToggle(fieldStack, toggle);
    }

    /** Field and toggle side by side, for callers that want them kept together. */
    public HBox asRow() {
        return new HBox(6, field, toggleButton);
    }
}

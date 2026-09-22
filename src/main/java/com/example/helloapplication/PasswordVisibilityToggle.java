package com.example.helloapplication;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * Pairs a masked PasswordField with a plain-text mirror, toggled by a
 * Show/Hide ToggleButton. The two fields share their text via a
 * bidirectional binding, so callers keep wiring the original PasswordField
 * passed in — its text is always current regardless of which one is
 * visible. Set any sizing (e.g. setMaxWidth) on the PasswordField before
 * wrapping it.
 */
public record PasswordVisibilityToggle(StackPane field, ToggleButton toggleButton) {

    /** Wraps a field with its own dedicated Show/Hide toggle. */
    public static PasswordVisibilityToggle wrap(PasswordField passwordField) {
        ToggleButton toggle = new ToggleButton("Show");
        toggle.selectedProperty().addListener(
                (obs, wasShowing, showing) -> toggle.setText(showing ? "Hide" : "Show"));

        StackPane fieldStack = wrap(passwordField, toggle);
        return new PasswordVisibilityToggle(fieldStack, toggle);
    }

    /**
     * Wraps a field so an already-existing, externally-owned ToggleButton
     * controls its visibility — for showing several fields together under
     * one Show/Hide control (e.g. a "new password"/"confirm password" pair,
     * so both can be checked at once with a single toggle).
     */
    public static StackPane wrap(PasswordField passwordField, ToggleButton sharedToggle) {
        TextField plainField = new TextField();
        plainField.textProperty().bindBidirectional(passwordField.textProperty());
        plainField.setMaxWidth(passwordField.getMaxWidth());
        plainField.setManaged(false);
        plainField.setVisible(false);

        StackPane fieldStack = new StackPane(passwordField, plainField);

        sharedToggle.selectedProperty().addListener((obs, wasShowing, showing) -> {
            passwordField.setVisible(!showing);
            passwordField.setManaged(!showing);
            plainField.setVisible(showing);
            plainField.setManaged(showing);
        });

        return fieldStack;
    }

    /** Field and toggle side by side, for callers that want them kept together. */
    public HBox asRow() {
        return new HBox(6, field, toggleButton);
    }
}

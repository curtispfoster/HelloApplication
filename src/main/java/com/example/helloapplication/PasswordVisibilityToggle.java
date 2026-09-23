package com.example.helloapplication;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

public record PasswordVisibilityToggle(StackPane field, ToggleButton toggleButton) {

    public static PasswordVisibilityToggle wrap(PasswordField passwordField) {
        ToggleButton toggle = new ToggleButton("Show");
        toggle.selectedProperty().addListener(
                (obs, wasShowing, showing) -> toggle.setText(showing ? "Hide" : "Show"));

        StackPane fieldStack = wrap(passwordField, toggle);
        return new PasswordVisibilityToggle(fieldStack, toggle);
    }

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

    public HBox asRow() {
        return new HBox(6, field, toggleButton);
    }
}

package com.example.helloapplication;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class OwnerSetupController {

    private final OwnerSetup ownerSetup;
    private final TextField username;
    private final PasswordField password;
    private final PasswordField confirmPassword;
    private final Label statusMessage;

    public OwnerSetupController(OwnerSetup ownerSetup, TextField username,
                                PasswordField password, PasswordField confirmPassword,
                                Label statusMessage) {
        this.ownerSetup = ownerSetup;
        this.username = username;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.statusMessage = statusMessage;
    }

    /** True once an owner exists (just created, or created elsewhere meanwhile). */
    public boolean handleCreateOwner() {
        if (!password.getText().equals(confirmPassword.getText())) {
            statusMessage.setText("Passwords do not match.");
            return false;
        }

        OwnerSetup.Status result = ownerSetup.createOwner(username.getText(), password.getText());
        switch (result) {
            case EMPTY          -> statusMessage.setText("Choose a username and password.");
            case WEAK_PASSWORD  -> statusMessage.setText(
                    "Password must be at least 8 characters and include a number and a symbol.");
            case DUPLICATE      -> statusMessage.setText("That username is taken.");
            case ALREADY_SET_UP -> statusMessage.setText("The owner account already exists.");
            case ERROR          -> statusMessage.setText("Could not reach the database.");
            case OK             -> statusMessage.setText("Owner account created.");
        }
        return result == OwnerSetup.Status.OK || result == OwnerSetup.Status.ALREADY_SET_UP;
    }
}

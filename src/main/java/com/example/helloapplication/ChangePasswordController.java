package com.example.helloapplication;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

public class ChangePasswordController {

    private final PasswordChange passwordChange;
    private final String username;
    private final PasswordField newPassword;
    private final PasswordField confirmPassword;
    private final Label statusMessage;

    public ChangePasswordController(PasswordChange passwordChange, String username,
                                     PasswordField newPassword, PasswordField confirmPassword,
                                     Label statusMessage) {
        this.passwordChange = passwordChange;
        this.username = username;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
        this.statusMessage = statusMessage;
    }

    public boolean handleChangePassword() {
        if (!newPassword.getText().equals(confirmPassword.getText())) {
            statusMessage.setText("Passwords do not match.");
            return false;
        }

        PasswordChange.Status result = passwordChange.changePassword(username, newPassword.getText());
        switch (result) {
            case EMPTY         -> statusMessage.setText("Enter a new password.");
            case WEAK_PASSWORD -> statusMessage.setText(
                    "Password must be at least 8 characters and include a number and a symbol.");
            case ERROR         -> statusMessage.setText("Could not reach the database.");
            case OK            -> statusMessage.setText("Password updated.");
        }
        return result == PasswordChange.Status.OK;
    }
}

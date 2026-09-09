package com.example.helloapplication;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Bridges the login UI to {@link Authenticator}.
 * Reads username/password fields, runs {@link Authenticator#checkLogin},
 * and writes the matching message into the status label.
 * Called from Login_View on button click or Enter in either field.
 */
public class LoginController {

    private final Authenticator auth;
    private final TextField username;
    private final PasswordField password;
    private final Label statusMessage;

    public LoginController(Authenticator auth,
                           TextField username,
                           PasswordField password,
                           Label statusMessage) {
        this.auth = auth;
        this.username = username;
        this.password = password;
        this.statusMessage = statusMessage;
    }

    /**
     * One login attempt. Updates the status label and returns the result
     * so Login_View can open Admin vs Home.
     */
    public Roles handleLogin() {
        Roles result = auth.checkLogin(username.getText(), password.getText());

        switch (result.status) {
            case EMPTY -> statusMessage.setText("Email/username or password is empty.");
            case WRONG -> statusMessage.setText("Wrong email/username or password.");
            case OK    -> statusMessage.setText("Signed in.");
            case ERROR -> statusMessage.setText("Could not reach the database.");
        }

        return result;
    }
}

package com.example.helloapplication;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

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

    public Roles handleLogin() {
        Roles result = auth.checkLogin(username.getText(), password.getText());

        switch (result.status) {
            case EMPTY          -> statusMessage.setText("Enter your username and password.");
            case WRONG          -> statusMessage.setText("Wrong username or password.");
            case WRONG_PASSWORD -> statusMessage.setText("Wrong password.");
            case OK             -> statusMessage.setText("Signed in.");
            case ERROR          -> statusMessage.setText("Could not reach the database.");
        }

        return result;
    }
}

package com.example.helloapplication;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class CreateAccountController {

    private final Registration registration;
    private final TextField name;
    private final TextField email;
    private final PasswordField password;
    private final Label statusMessage;

    public CreateAccountController(Registration registration,
                                    TextField name,
                                    TextField email,
                                    PasswordField password,
                                    Label statusMessage) {
        this.registration = registration;
        this.name = name;
        this.email = email;
        this.password = password;
        this.statusMessage = statusMessage;
    }

    public Registration.Status handleCreateAccount() {
        Registration.Status result = registration.createAccount(
                name.getText(), email.getText(), password.getText());

        switch (result) {
            case EMPTY         -> statusMessage.setText("Name, email, and password are all required.");
            case WEAK_PASSWORD -> statusMessage.setText(
                    "Password must be at least 8 characters and include a number and a symbol.");
            case DUPLICATE     -> statusMessage.setText("An account with that email already exists.");
            case OK            -> statusMessage.setText("Account created.");
            case ERROR         -> statusMessage.setText("Could not reach the database.");
        }

        return result;
    }
}

package com.example.helloapplication;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Landing screen for a signed-in USER account.
 * Placeholder for now — just proves login routes here instead of
 * staying on the login screen.
 */
public class Home_View extends Application {

    private final String username;

    public Home_View(String username) {
        this.username = username;
    }

    @Override
    public void start(Stage primaryStage) {
        Label welcome = new Label("Welcome, " + username + ".");

        Button logout = new Button("Log out");
        logout.setOnAction(e -> {
            try {
                primaryStage.close();
                new Login_View().start(new Stage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        VBox root = new VBox(16, welcome, logout);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setPrefWidth(400);
        root.setPrefHeight(250);

        primaryStage.setTitle("Home");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }
}

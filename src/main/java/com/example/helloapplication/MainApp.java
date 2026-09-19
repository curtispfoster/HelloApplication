package com.example.helloapplication;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * The application's one and only JavaFX entry point — launched exactly
 * once via {@link Application#launch} (see {@link Launcher}). Every
 * screen (Login_View, Create_View, Home_View, Admin_View,
 * ChangePassword_View) is a plain class with a show(Stage) method that
 * reuses this same Stage instead of opening its own; navigating between
 * them just swaps the Scene shown on it.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        new Login_View().show(primaryStage);
    }
}

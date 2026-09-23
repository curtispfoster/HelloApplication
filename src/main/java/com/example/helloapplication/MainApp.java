package com.example.helloapplication;

import javafx.application.Application;
import javafx.stage.Stage;

public class MainApp extends Application {

    public MainApp() {
    }

    @Override
    public void start(Stage primaryStage) {
        new Login_View().show(primaryStage);
    }
}

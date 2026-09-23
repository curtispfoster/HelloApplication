package com.example.helloapplication;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

final class Views {

    private static final Logger LOGGER = Logger.getLogger(Views.class.getName());

    private Views() {
    }

    static void addStylesheets(Scene scene, String... names) {
        for (String name : names) {
            scene.getStylesheets().add(Objects.requireNonNull(Views.class.getResource(name)).toExternalForm());
        }
    }

    static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    static void setStatus(Label status, String text, String styleClass) {
        status.setText(text);
        status.getStyleClass().removeAll("status-ok", "status-error");
        if (styleClass != null) {
            status.getStyleClass().add(styleClass);
        }
    }

    static Label recordKey(String column, String rowClass) {
        Label key = new Label(column);
        key.getStyleClass().addAll("record-key", rowClass);
        key.setMaxWidth(Double.MAX_VALUE);
        key.setMaxHeight(Double.MAX_VALUE);
        return key;
    }

    static HBox recordValue(String rowClass, Node... children) {
        HBox cell = new HBox(6, children);
        cell.getStyleClass().addAll("record-value", rowClass);
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    static void navigate(String screen, Runnable show) {
        try {
            show.run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Could not open " + screen, e);
        }
    }

    static void openLandingView(Stage stage, Roles user) {
        if (user.isAdmin()) {
            navigate("Admin_View", () -> new Admin_View(user).show(stage));
        } else {
            navigate("Home_View", () -> new Home_View(user).show(stage));
        }
    }

    static VBox accountBlock(Stage stage, Roles user) {
        Label name = new Label(user.username);
        name.getStyleClass().add("account-name");
        HBox nameRow = new HBox(8, name);
        if (user.isAdmin()) {
            Label role = new Label(user.isOwner() ? "Owner" : "Admin");
            role.getStyleClass().add("panel-caption");
            nameRow.getChildren().add(role);
        }

        Hyperlink changePassword = new Hyperlink("Change password");
        changePassword.setOnAction(e -> navigate("ChangePassword_View",
                () -> new ChangePassword_View(user, false).show(stage)));
        Hyperlink logout = new Hyperlink("Log out");
        logout.setOnAction(e -> navigate("Login_View", () -> new Login_View().show(stage)));

        VBox account = new VBox(6, nameRow, changePassword, logout);
        account.getStyleClass().add("account-block");
        return account;
    }
}

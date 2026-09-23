package com.example.helloapplication;

import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;

final class SchemaDiagram {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    private static final double TABLE_X = 16;
    private static final double TABLE_WIDTH = 232;
    private static final double HEADER_HEIGHT = 26;
    private static final double ROW_HEIGHT = 22;
    private static final double TRUNK_X = 4;

    private static final String[][] USERS_COLUMNS = {
            {"UserID", "INTEGER PK"},
            {"Username", "TEXT UNIQUE"},
            {"Password", "TEXT"},
            {"Role", "TEXT"},
            {"Name", "TEXT"},
            {"MustChangePassword", "INTEGER"},
    };
    private static final int ROLE_ROW = 3;

    private static final String[][] ROLE_VALUES = {
            {"USER", "default"},
            {"ADMIN", ""},
            {"OWNER", "top"},
    };

    private SchemaDiagram() {
    }

    static Pane build() {
        VBox users = table("Users", USERS_COLUMNS, ROW_HEIGHT);
        users.relocate(TABLE_X, 0);
        users.setPrefWidth(TABLE_WIDTH);

        double roleValuesY = HEADER_HEIGHT + USERS_COLUMNS.length * ROW_HEIGHT + 34;
        VBox roles = table("Role values", ROLE_VALUES, 20);
        roles.relocate(40, roleValuesY);
        roles.setPrefWidth(160);

        double fromY = HEADER_HEIGHT + ROLE_ROW * ROW_HEIGHT + ROW_HEIGHT / 2;
        double toY = roleValuesY + HEADER_HEIGHT / 2;
        Polyline connector = new Polyline(
                TABLE_X, fromY,
                TRUNK_X, fromY,
                TRUNK_X, toY,
                40, toY);
        connector.getStyleClass().add("schema-connector");
        Circle joint = new Circle(TABLE_X, fromY, 2.5);
        joint.getStyleClass().add("schema-joint");

        Pane diagram = new Pane(connector, users, roles, joint);
        diagram.setPrefSize(TABLE_X + TABLE_WIDTH, roleValuesY + HEADER_HEIGHT + ROLE_VALUES.length * 20 + 2);
        diagram.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return diagram;
    }

    static VBox sidePanel(Pane diagram) {
        Label wordmark = new Label("Database Manager");
        wordmark.getStyleClass().add("wordmark");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label caption = new Label("Schema of data/users.db");
        caption.getStyleClass().add("panel-caption");

        VBox panel = new VBox(16, wordmark, spacer, diagram, caption);
        panel.getStyleClass().add("side-panel");
        panel.setPrefWidth(320);
        panel.setMinWidth(320);
        return panel;
    }

    static void highlightWhileFocused(Pane diagram, String column, ObservableValue<Boolean> focused) {
        Node row = diagram.lookup("#" + rowId(column));
        if (row != null) {
            focused.addListener((obs, was, now) -> row.pseudoClassStateChanged(ACTIVE, now));
        }
    }

    static String rowId(String name) {
        return "schema-row-" + name.replace(' ', '-');
    }

    private static VBox table(String title, String[][] rows, double rowHeight) {
        HBox header = row(title, "", "schema-table-header");
        header.setMinHeight(HEADER_HEIGHT);
        header.setPrefHeight(HEADER_HEIGHT);

        VBox table = new VBox(header);
        table.getStyleClass().add("schema-table");
        for (String[] r : rows) {
            HBox line = row(r[0], r[1], "schema-row");
            line.setMinHeight(rowHeight);
            line.setPrefHeight(rowHeight);
            table.getChildren().add(line);
        }
        return table;
    }

    private static HBox row(String name, String note, String styleClass) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("schema-name");
        Label noteLabel = new Label(note);
        noteLabel.getStyleClass().add("schema-note");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, nameLabel, spacer, noteLabel);
        row.getStyleClass().add(styleClass);
        row.setId(rowId(name));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}

package com.example.helloapplication;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

final class ResultTable {

    private ResultTable() {
    }

    static TableView<List<String>> create(String placeholder) {
        TableView<List<String>> table = new TableView<>();
        table.getStyleClass().add("preview-table");
        table.setFixedCellSize(28); // dense rows; also lets TableView skip per-row measuring
        table.setPlaceholder(new Label(placeholder));
        return table;
    }

    static void fill(TableView<List<String>> table, DatabaseBrowser.TablePreview preview) {
        table.getColumns().clear();
        for (int i = 0; i < preview.columns().size(); i++) {
            int index = i;
            TableColumn<List<String>, String> column = new TableColumn<>(preview.columns().get(i));
            column.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().get(index)));
            column.setCellFactory(col -> new PreviewCell());
            column.setPrefWidth(150);
            table.getColumns().add(column);
        }
        table.getItems().setAll(preview.rows());
        table.scrollTo(0);
    }

    private static final class PreviewCell extends TableCell<List<String>, String> {
        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            setText(empty ? null : value);
            getStyleClass().remove("null-cell");
            if (!empty && "NULL".equals(value)) {
                getStyleClass().add("null-cell");
            }
        }
    }
}

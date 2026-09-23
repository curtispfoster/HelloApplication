package com.example.helloapplication;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

public final class StatusLabelAlignment {

    private StatusLabelAlignment() {
    }

    public static void applyTo(Label label) {
        Platform.runLater(() -> {
            Runnable updateAlignment = () -> {
                String text = label.getText() == null ? "" : label.getText();
                Text unwrapped = new Text(text);
                unwrapped.setFont(label.getFont());
                boolean wraps = unwrapped.getLayoutBounds().getWidth() > label.getMaxWidth();

                label.setTextAlignment(wraps ? TextAlignment.LEFT : TextAlignment.CENTER);
                label.setAlignment(wraps ? Pos.CENTER_LEFT : Pos.CENTER);
            };

            label.textProperty().addListener((obs, oldText, newText) -> updateAlignment.run());
            updateAlignment.run();
        });
    }
}

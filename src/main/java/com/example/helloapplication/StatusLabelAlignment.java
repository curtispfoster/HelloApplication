package com.example.helloapplication;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * Keeps a status Label centered while its message fits on one line, and
 * switches to left-aligned once wrapping pushes it onto more than one —
 * centered multi-line text reads as ragged, left-aligned reads as a
 * normal paragraph. Call after the label's wrapping width (setMaxWidth)
 * is already set, since that's what decides where it wraps.
 *
 * Sets both {@code textAlignment} and {@code alignment}: textAlignment
 * only controls how multiple lines justify relative to each other and
 * has no effect on a single line, which is instead positioned within
 * the label's box by alignment (default CENTER_LEFT) — so a one-line
 * message needs alignment set to CENTER to actually appear centered.
 *
 * Setup is deferred via {@link Platform#runLater}: called from show(),
 * the label isn't part of a shown Scene yet, so label.getFont() can
 * return unresolved/default font metrics that don't match what's
 * actually rendered once CSS applies. Deferring one pulse measures
 * against the real, resolved font every time.
 *
 * Whether the text wraps is decided by comparing an unwrapped Text's
 * natural width against the label's max width — not by comparing a
 * wrapped Text's height against a separate single-line reference's
 * height. The height comparison looked reasonable but wasn't reliable:
 * a Text with wrappingWidth set can report a slightly different line
 * height than one without, even when both are genuinely one line, which
 * tripped the "wraps" threshold for text that never actually wrapped.
 */
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

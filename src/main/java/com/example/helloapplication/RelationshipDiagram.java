package com.example.helloapplication;

import com.example.helloapplication.DatabaseBrowser.ForeignKey;
import com.example.helloapplication.DatabaseBrowser.TableRef;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class RelationshipDiagram {

    private static final double PAD = 24;
    private static final double BOX_WIDTH = 170;
    private static final double HEADER_HEIGHT = 30;
    private static final double ROW_HEIGHT = 24;
    private static final double GAP_X = 80;
    private static final double GAP_Y = 28;
    private static final double PAD_LEFT = PAD + GAP_X / 2;

    private RelationshipDiagram() {
    }

    static Pane build(List<ForeignKey> keys, Consumer<ForeignKey> onOpen) {
        Map<TableRef, List<String>> columns = linkedColumns(keys);
        Map<TableRef, Integer> levels = levels(columns.keySet(), keys);

        // Place boxes: one column per level, stacked top to bottom in an order that keeps lines from crossing.
        List<List<TableRef>> byLevel = orderWithinLevels(columns.keySet(), levels, keys);
        Map<TableRef, double[]> origin = new HashMap<>();
        double width = 0;
        double height = 0;
        List<TableRef> ordered = new ArrayList<>();
        for (int level = 0; level < byLevel.size(); level++) {
            double x = PAD_LEFT + level * (BOX_WIDTH + GAP_X);
            double y = PAD;
            for (TableRef table : byLevel.get(level)) {
                origin.put(table, new double[]{x, y});
                ordered.add(table);
                y += HEADER_HEIGHT + columns.get(table).size() * ROW_HEIGHT + GAP_Y;
            }
            width = Math.max(width, x + BOX_WIDTH + PAD);
            height = Math.max(height, y - GAP_Y + PAD);
        }

        Pane pane = new Pane();
        pane.getStyleClass().add("relationship-diagram");
        List<Group> edges = new ArrayList<>();
        for (ForeignKey key : keys) {
            edges.add(edge(key, origin, columns, onOpen));
        }
        pane.getChildren().addAll(edges);
        for (TableRef table : ordered) {
            VBox box = box(table, columns.get(table));
            double[] xy = origin.get(table);
            box.relocate(xy[0], xy[1]);
            pane.getChildren().add(box);
        }
        pane.setPrefSize(width, height);
        pane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return pane;
    }

    private static Map<TableRef, List<String>> linkedColumns(List<ForeignKey> keys) {
        Map<TableRef, Set<String>> keysOf = new LinkedHashMap<>();
        Map<TableRef, Set<String>> refsOf = new LinkedHashMap<>();
        for (ForeignKey k : keys) {
            keysOf.computeIfAbsent(k.parent(), t -> new LinkedHashSet<>()).add(k.parentColumn());
            refsOf.computeIfAbsent(k.child(), t -> new LinkedHashSet<>()).add(k.childColumn());
            keysOf.computeIfAbsent(k.child(), t -> new LinkedHashSet<>());
            refsOf.computeIfAbsent(k.parent(), t -> new LinkedHashSet<>());
        }
        Map<TableRef, List<String>> columns = new LinkedHashMap<>();
        for (TableRef table : keysOf.keySet()) {
            Set<String> all = new LinkedHashSet<>(keysOf.get(table));
            all.addAll(refsOf.get(table));
            columns.put(table, new ArrayList<>(all));
        }
        return columns;
    }

    private static List<List<TableRef>> orderWithinLevels(Set<TableRef> tables, Map<TableRef, Integer> levels,
                                                          List<ForeignKey> keys) {
        int maxLevel = levels.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<List<TableRef>> byLevel = new ArrayList<>();
        for (int i = 0; i <= maxLevel; i++) {
            byLevel.add(new ArrayList<>());
        }
        for (TableRef table : tables) {
            byLevel.get(levels.get(table)).add(table);
        }
        for (List<TableRef> level : byLevel) {
            level.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        }

        for (int pass = 0; pass < 4; pass++) {
            boolean rightward = pass % 2 == 0;
            for (int step = 0; step <= maxLevel; step++) {
                int i = rightward ? step : maxLevel - step;
                Map<TableRef, Double> position = positions(byLevel);
                List<TableRef> level = byLevel.get(i);
                Map<TableRef, Double> weight = new HashMap<>();
                for (TableRef table : level) {
                    double sum = 0;
                    int count = 0;
                    for (ForeignKey k : keys) {
                        TableRef other = k.child().equals(table) ? k.parent() : k.parent().equals(table) ? k.child() : null;
                        if (other != null && !other.equals(table) && levels.get(other) != i) {
                            sum += position.get(other);
                            count++;
                        }
                    }
                    weight.put(table, count == 0 ? position.get(table) : sum / count);
                }
                level.sort((a, b) -> Double.compare(weight.get(a), weight.get(b)));
            }
        }
        return byLevel;
    }

    private static Map<TableRef, Double> positions(List<List<TableRef>> byLevel) {
        Map<TableRef, Double> position = new HashMap<>();
        for (List<TableRef> level : byLevel) {
            for (int j = 0; j < level.size(); j++) {
                position.put(level.get(j), (j + 0.5) / level.size());
            }
        }
        return position;
    }

    private static Map<TableRef, Integer> levels(Set<TableRef> tables, List<ForeignKey> keys) {
        Map<TableRef, Integer> levels = new HashMap<>();
        for (TableRef table : tables) {
            level(table, keys, levels, new HashSet<>());
        }
        return levels;
    }

    private static int level(TableRef table, List<ForeignKey> keys, Map<TableRef, Integer> memo, Set<TableRef> visiting) {
        Integer known = memo.get(table);
        if (known != null) {
            return known;
        }
        if (!visiting.add(table)) {
            return 0; // a cycle: stop here rather than recurse forever
        }
        int level = 0;
        for (ForeignKey k : keys) {
            if (k.child().equals(table) && !k.parent().equals(table)) {
                level = Math.max(level, level(k.parent(), keys, memo, visiting) + 1);
            }
        }
        visiting.remove(table);
        memo.put(table, level);
        return level;
    }

    private static VBox box(TableRef table, List<String> columns) {
        Label title = new Label(table.displayName());
        title.getStyleClass().add("rel-table-name");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinHeight(HEADER_HEIGHT);
        title.setPrefHeight(HEADER_HEIGHT);

        VBox box = new VBox(title);
        box.getStyleClass().add("rel-table");
        box.setPrefWidth(BOX_WIDTH);
        for (String column : columns) {
            Label name = new Label(column);
            name.getStyleClass().add("rel-column");
            name.setMinWidth(0);
            HBox row = new HBox(name);
            row.getStyleClass().add("rel-row");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinHeight(ROW_HEIGHT);
            row.setPrefHeight(ROW_HEIGHT);
            row.setMaxHeight(ROW_HEIGHT);
            HBox.setHgrow(name, Priority.ALWAYS);
            box.getChildren().add(row);
        }
        return box;
    }

    private static Group edge(ForeignKey key, Map<TableRef, double[]> origin, Map<TableRef, List<String>> columns,
                              Consumer<ForeignKey> onOpen) {
        double[] child = origin.get(key.child());
        double[] parent = origin.get(key.parent());
        double childY = child[1] + HEADER_HEIGHT + columns.get(key.child()).indexOf(key.childColumn()) * ROW_HEIGHT
                + ROW_HEIGHT / 2;
        double parentY = parent[1] + HEADER_HEIGHT + columns.get(key.parent()).indexOf(key.parentColumn()) * ROW_HEIGHT
                + ROW_HEIGHT / 2;

        double startX;
        double endX;
        double startPull;
        double endPull;
        if (child[0] > parent[0]) {        // usual case: child to the right, line runs left into the parent
            startX = child[0];
            endX = parent[0] + BOX_WIDTH;
            startPull = -GAP_X / 2;
            endPull = GAP_X / 2;
        } else if (child[0] < parent[0]) { // a cycle put the child on the left
            startX = child[0] + BOX_WIDTH;
            endX = parent[0];
            startPull = GAP_X / 2;
            endPull = -GAP_X / 2;
        } else {                           // same column (self-reference or cycle): loop out to the left
            startX = child[0];
            endX = parent[0];
            startPull = -GAP_X / 2;
            endPull = -GAP_X / 2;
        }

        CubicCurve line = curve(startX, childY, startX + startPull, endX + endPull, endX, parentY);
        line.getStyleClass().add("rel-line");
        CubicCurve hit = curve(startX, childY, startX + startPull, endX + endPull, endX, parentY);
        hit.getStyleClass().add("rel-hit");
        Circle end = new Circle(endX, parentY, 3.5);
        end.getStyleClass().add("rel-end");

        Group edge = new Group(line, end, hit);
        edge.getStyleClass().add("rel-edge");
        edge.setCursor(Cursor.HAND);
        Tooltip.install(hit, new Tooltip(key.child().displayName() + "." + key.childColumn() + " points at "
                + key.parent().displayName() + "." + key.parentColumn() + ". Click to preview the joined rows."));
        hit.setOnMouseClicked(e -> onOpen.accept(key));
        return edge;
    }

    private static CubicCurve curve(double startX, double startY, double control1X, double control2X,
                                    double endX, double endY) {
        CubicCurve curve = new CubicCurve(startX, startY, control1X, startY, control2X, endY, endX, endY);
        curve.setFill(null);
        return curve;
    }
}

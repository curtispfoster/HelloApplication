package com.example.helloapplication;

import com.example.helloapplication.DatabaseBrowser.TablePreview;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class ChartMaker {

    enum Kind {
        BAR("Bar chart"), LINE("Line chart"), PIE("Pie chart"), SCATTER("Scatter plot");

        final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum Measure {
        COUNT("Count of rows", "COUNT"), SUM("Sum of", "SUM"), AVERAGE("Average of", "AVG"),
        MIN("Minimum of", "MIN"), MAX("Maximum of", "MAX");

        final String label;
        final String function;

        Measure(String label, String function) {
            this.label = label;
            this.function = function;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    record Spec(Kind kind, String x, Measure measure, String y) {}

    static final int PIE_SLICES = 10;

    static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");

    private ChartMaker() {
    }

    static int limit(Kind kind) {
        return switch (kind) {
            case BAR -> 40;
            case LINE -> 2000;
            case PIE -> 1000;
            case SCATTER -> 5000;
        };
    }

    static String problem(Spec spec, List<String> columns, List<String> numericColumns) {
        if (spec.x() == null || !columns.contains(spec.x())) {
            return spec.kind() == Kind.SCATTER ? "Pick the column to plot across." : "Pick a column to group by.";
        }
        boolean needsY = spec.kind() == Kind.SCATTER || spec.measure() != Measure.COUNT;
        if (needsY && (spec.y() == null || !columns.contains(spec.y()))) {
            return numericColumns.isEmpty()
                    ? "This result has no number columns to measure. Try Count of rows."
                    : "Pick a number column to measure.";
        }
        if (needsY && !numericColumns.contains(spec.y())) {
            return spec.y() + " isn't a number column.";
        }
        if (spec.kind() == Kind.SCATTER && !numericColumns.contains(spec.x())) {
            return "A scatter plot needs numbers on both axes; " + spec.x() + " isn't a number column.";
        }
        return null;
    }

    static String sql(String query, Spec spec) {
        String select = DatabaseBrowser.checkQuery(query);
        String x = quote(spec.x());
        if (spec.kind() == Kind.SCATTER) {
            String y = quote(spec.y());
            return "SELECT " + x + ", " + y + " FROM (\n" + select + "\n) WHERE " + x
                    + " IS NOT NULL AND " + y + " IS NOT NULL";
        }
        String value = spec.measure() == Measure.COUNT
                ? "COUNT(*)" : spec.measure().function + "(" + quote(spec.y()) + ")";
        // Positions, not aliases: a column called "value" must not be mistaken for the aggregate.
        String order = spec.kind() == Kind.LINE ? "1" : "2 DESC, 1";
        return "SELECT " + x + ", " + value + " FROM (\n" + select + "\n) GROUP BY 1 ORDER BY " + order;
    }

    private static String quote(String column) {
        return DatabaseBrowser.quoteIdentifier(column, "\"");
    }

    static List<String> numericColumns(TablePreview result) {
        List<String> numeric = new ArrayList<>();
        for (int c = 0; c < result.columns().size(); c++) {
            boolean any = false;
            boolean allNumbers = true;
            for (List<String> row : result.rows()) {
                String value = row.get(c);
                if (value == null || value.equals("NULL")) {
                    continue;
                }
                any = true;
                if (parse(value) == null) {
                    allNumbers = false;
                    break;
                }
            }
            if (any && allNumbers) {
                numeric.add(result.columns().get(c));
            }
        }
        return numeric;
    }

    static String describe(Spec spec, int shown, long total) {
        String what = spec.kind() == Kind.SCATTER
                ? spec.y() + " against " + spec.x()
                : axisTitle(spec) + " by " + spec.x();
        if (spec.kind() == Kind.SCATTER) {
            return what + ": " + (shown < total
                    ? String.format("showing %,d of %,d points.", shown, total)
                    : ViewText.plural(total, "point") + ".");
        }
        if (total == 0) {
            return what + ": the query returned no rows.";
        }
        if (shown < total) {
            return what + String.format(spec.kind() == Kind.LINE
                    ? ": showing the first %,d of %,d groups." : ": showing the %,d biggest of %,d groups.", shown, total);
        }
        String text = what + ": " + ViewText.plural(total, "group") + ".";
        if (spec.kind() == Kind.PIE && total > PIE_SLICES) {
            text += " The smallest " + (total - PIE_SLICES + 1) + " are added up as Other.";
        }
        return text;
    }

    static String axisTitle(Spec spec) {
        return spec.measure() == Measure.COUNT ? "Count of rows" : spec.measure().label + " " + spec.y();
    }

    static Chart build(Spec spec, TablePreview data, boolean numericX) {
        Chart chart = switch (spec.kind()) {
            case BAR -> bar(spec, data);
            case LINE -> numericX ? numericLine(spec, data) : categoryLine(spec, data);
            case PIE -> pie(data);
            case SCATTER -> scatter(spec, data);
        };
        chart.setAnimated(false);
        chart.getStyleClass().add("result-chart");
        return chart;
    }

    private static BarChart<String, Number> bar(Spec spec, TablePreview data) {
        BarChart<String, Number> chart = new BarChart<>(categoryAxis(spec.x()),
                numberAxis(axisTitle(spec), true));
        chart.getData().add(categorySeries(data));
        chart.setLegendVisible(false);
        chart.setCategoryGap(6);
        return chart;
    }

    private static LineChart<String, Number> categoryLine(Spec spec, TablePreview data) {
        LineChart<String, Number> chart = new LineChart<>(categoryAxis(spec.x()),
                numberAxis(axisTitle(spec), false));
        chart.getData().add(categorySeries(data));
        chart.setLegendVisible(false);
        chart.setCreateSymbols(data.rows().size() <= 60);
        return chart;
    }

    private static LineChart<Number, Number> numericLine(Spec spec, TablePreview data) {
        LineChart<Number, Number> chart = new LineChart<>(numberAxis(spec.x(), false),
                numberAxis(axisTitle(spec), false));
        chart.getData().add(numberSeries(data));
        chart.setLegendVisible(false);
        chart.setCreateSymbols(data.rows().size() <= 60);
        return chart;
    }

    private static ScatterChart<Number, Number> scatter(Spec spec, TablePreview data) {
        ScatterChart<Number, Number> chart = new ScatterChart<>(numberAxis(spec.x(), false),
                numberAxis(spec.y(), false));
        chart.getData().add(numberSeries(data));
        chart.setLegendVisible(false);
        return chart;
    }

    private static PieChart pie(TablePreview data) {
        PieChart chart = new PieChart();
        double other = 0;
        int slices = 0;
        Set<String> used = new HashSet<>();
        for (List<String> row : data.rows()) {
            Double value = parse(row.get(1));
            if (value == null || value <= 0) {
                continue;
            }
            if (slices < PIE_SLICES - 1 || data.rows().size() <= PIE_SLICES) {
                chart.getData().add(new PieChart.Data(uniqueLabel(row.get(0), used), value));
                slices++;
            } else {
                other += value;
            }
        }
        if (other > 0) {
            chart.getData().add(new PieChart.Data("Other", other));
        }
        chart.setLegendVisible(false);
        chart.setLabelsVisible(true);
        return chart;
    }

    private static XYChart.Series<String, Number> categorySeries(TablePreview data) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Set<String> used = new HashSet<>();
        for (List<String> row : data.rows()) {
            Double value = parse(row.get(1));
            if (value != null) {
                series.getData().add(new XYChart.Data<>(uniqueLabel(row.get(0), used), value));
            }
        }
        return series;
    }

    private static XYChart.Series<Number, Number> numberSeries(TablePreview data) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (List<String> row : data.rows()) {
            Double x = parse(row.get(0));
            Double y = parse(row.get(1));
            if (x != null && y != null) {
                series.getData().add(new XYChart.Data<>(x, y));
            }
        }
        return series;
    }

    static String uniqueLabel(String value, Set<String> used) {
        String base = value == null || value.equals("NULL") ? "(empty)" : value;
        String label = base;
        for (int n = 2; !used.add(label); n++) {
            label = base + " (" + n + ")";
        }
        return label;
    }

    private static CategoryAxis categoryAxis(String title) {
        CategoryAxis axis = new CategoryAxis();
        axis.setLabel(title);
        axis.setAnimated(false);
        return axis;
    }

    private static NumberAxis numberAxis(String title, boolean fromZero) {
        NumberAxis axis = new NumberAxis();
        axis.setLabel(title);
        axis.setAnimated(false);
        axis.setForceZeroInRange(fromZero);
        return axis;
    }

    static Double parse(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        if (!DECIMAL.matcher(text).matches()) {
            return null;
        }
        try {
            double d = Double.parseDouble(text);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

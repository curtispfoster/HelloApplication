package com.example.helloapplication;

import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * The SQL help tab on Home: short examples of the SQL most questions need, written against the sample shop so
 * each one runs as-is there. Kept as plain data so a test can run every example against the sample database.
 */
final class SqlCheatSheet {

    record Example(String title, String explanation, String sql) {}

    record Section(String title, List<Example> examples) {}

    static final List<Section> SECTIONS = List.of(
            new Section("Getting rows", List.of(
                    new Example("Every row and column", "* means all columns.",
                            "SELECT * FROM customers"),
                    new Example("Only some columns", "List them, separated by commas.",
                            "SELECT first_name, last_name, city FROM customers"),
                    new Example("Rename a column in the result", "AS gives a column a new name in the result.",
                            "SELECT first_name AS name, city AS lives_in FROM customers"),
                    new Example("Each value once", "DISTINCT drops repeated rows.",
                            "SELECT DISTINCT city FROM customers"))),
            new Section("Filtering with WHERE", List.of(
                    new Example("Exact match", "Text goes in single quotes.",
                            "SELECT * FROM orders WHERE status = 'delivered'"),
                    new Example("A range of numbers", "BETWEEN includes both ends. Also: <, <=, >, >=, <> (not equal).",
                            "SELECT name, unit_price FROM products WHERE unit_price BETWEEN 10 AND 50"),
                    new Example("Several conditions", "AND needs both, OR needs either. Brackets group them.",
                            "SELECT * FROM orders WHERE status = 'shipped' OR (status = 'delivered' AND shipped_on IS NULL)"),
                    new Example("One of a list", "IN is a shorter way to write several ORs.",
                            "SELECT * FROM orders WHERE status IN ('shipped', 'processing')"),
                    new Example("Text patterns", "% matches any text, _ one character. LIKE ignores upper/lower case.",
                            "SELECT first_name, last_name FROM customers WHERE first_name LIKE 'A%'"),
                    new Example("Missing values", "Empty cells are NULL. Use IS NULL / IS NOT NULL; = NULL never matches.",
                            "SELECT * FROM customers WHERE city IS NULL"))),
            new Section("Sorting and limiting", List.of(
                    new Example("Biggest first, top 10", "ORDER BY sorts (DESC for high to low); LIMIT keeps the first rows.",
                            "SELECT name, unit_price FROM products ORDER BY unit_price DESC LIMIT 10"))),
            new Section("Counting and summaries", List.of(
                    new Example("How many rows", "COUNT(*) counts rows; COUNT(column) counts values that aren't empty.",
                            "SELECT COUNT(*) AS orders FROM orders"),
                    new Example("Per group", "GROUP BY makes one row per value. Good for bar and pie charts.",
                            "SELECT status, COUNT(*) AS orders FROM orders GROUP BY status ORDER BY orders DESC"),
                    new Example("Sum, average, smallest, largest", "ROUND(x, 2) keeps two decimals.",
                            "SELECT category, COUNT(*) AS products, ROUND(AVG(unit_price), 2) AS average_price, "
                                    + "MIN(unit_price) AS cheapest, MAX(unit_price) AS dearest "
                                    + "FROM products GROUP BY category"),
                    new Example("Only some groups", "HAVING filters groups after GROUP BY; WHERE filters rows before.",
                            "SELECT customer_id, COUNT(*) AS orders FROM orders GROUP BY customer_id "
                                    + "HAVING COUNT(*) >= 5 ORDER BY orders DESC"))),
            new Section("Joining tables", List.of(
                    new Example("Rows that match in both",
                            "JOIN … ON pairs each order with its customer. The short names (o, c) save typing.",
                            "SELECT o.order_id, c.first_name, c.last_name, o.status FROM orders o "
                                    + "JOIN customers c ON c.customer_id = o.customer_id"),
                    new Example("Keep rows with no match",
                            "LEFT JOIN keeps every customer, even ones with no orders (they count 0).",
                            "SELECT c.first_name, c.last_name, COUNT(o.order_id) AS orders FROM customers c "
                                    + "LEFT JOIN orders o ON o.customer_id = c.customer_id "
                                    + "GROUP BY c.customer_id ORDER BY orders"),
                    new Example("Three tables and a total", "Join as many as you need, then group.",
                            "SELECT p.category, ROUND(SUM(i.quantity * i.unit_price), 2) AS revenue "
                                    + "FROM order_items i JOIN products p ON p.product_id = i.product_id "
                                    + "GROUP BY p.category ORDER BY revenue DESC"))),
            new Section("Dates and text", List.of(
                    new Example("Per month", "Dates are text like 2024-06-30; strftime picks parts out. Good for line charts.",
                            "SELECT strftime('%Y-%m', placed_on) AS month, COUNT(*) AS orders FROM orders "
                                    + "GROUP BY month ORDER BY month"),
                    new Example("Between two dates", "Dates written YYYY-MM-DD sort and compare correctly as text.",
                            "SELECT * FROM orders WHERE placed_on >= '2024-06-01' AND placed_on < '2024-07-01'"),
                    new Example("Days between dates", "julianday turns a date into a day number you can subtract.",
                            "SELECT order_id, julianday(shipped_on) - julianday(placed_on) AS days_to_ship "
                                    + "FROM orders WHERE shipped_on IS NOT NULL"),
                    new Example("Joining text", "|| sticks text together. Also UPPER, LOWER, LENGTH, SUBSTR, TRIM.",
                            "SELECT first_name || ' ' || last_name AS full_name, UPPER(city) AS city FROM customers"))),
            new Section("Going further", List.of(
                    new Example("Labels from conditions", "CASE picks a value by the first condition that's true.",
                            "SELECT name, unit_price, CASE WHEN unit_price < 10 THEN 'budget' "
                                    + "WHEN unit_price < 50 THEN 'standard' ELSE 'premium' END AS price_band FROM products"),
                    new Example("Compare with a summary", "A query in brackets can be used as a value.",
                            "SELECT name, unit_price FROM products "
                                    + "WHERE unit_price > (SELECT AVG(unit_price) FROM products) ORDER BY unit_price"),
                    new Example("Build it in steps", "WITH names a query so the next one can use it like a table.",
                            "WITH totals AS (SELECT order_id, SUM(quantity * unit_price) AS total FROM order_items "
                                    + "GROUP BY order_id) SELECT ROUND(AVG(total), 2) AS average_order FROM totals"))));

    static final List<String> TIPS = List.of(
            "Ctrl+Enter runs the query. One SELECT at a time; nothing you run can change the data.",
            "Names with spaces or odd characters go in double quotes: \"Order Date\". Text values go in single quotes.",
            "Double-click a column in the side panel to add its name to the query.",
            "The Rows tab shows up to 500 rows, but charts use the whole result.",
            "For a chart, return one column to group by and one number column, like the Per group example.");

    private SqlCheatSheet() {
    }

    /**
     * The tab's content. {@code use} puts an example in the editor, {@code run} also runs it, and
     * {@code openSample} is offered when the open dataset isn't the sample shop the examples are written for.
     */
    static ScrollPane build(Consumer<String> use, Consumer<String> run, Runnable openSample) {
        VBox content = new VBox(18);
        content.getStyleClass().add("cheat-sheet");

        Label intro = muted("Examples of the SQL most questions need. They use the sample shop's tables (customers, "
                + "orders, order_items, products, suppliers), so they run as-is there; for your own data, swap in "
                + "your table and column names from the side panel.");
        Hyperlink sample = new Hyperlink("Open the sample shop");
        sample.setOnAction(e -> openSample.run());
        content.getChildren().add(new VBox(4, intro, sample));

        VBox tips = new VBox(4, title("Tips"));
        for (String tip : TIPS) {
            tips.getChildren().add(muted("• " + tip));
        }
        content.getChildren().add(tips);

        for (Section section : SECTIONS) {
            VBox box = new VBox(12, title(section.title()));
            for (Example example : section.examples()) {
                Label name = new Label(example.title());
                name.getStyleClass().add("cheat-title");
                Label explanation = muted(example.explanation());
                Label sql = new Label(example.sql());
                sql.getStyleClass().add("cheat-code");
                sql.setWrapText(true);
                sql.setMaxWidth(Double.MAX_VALUE);
                sql.setMinHeight(Region.USE_PREF_SIZE);
                Hyperlink useLink = new Hyperlink("Put in editor");
                useLink.setOnAction(e -> use.accept(example.sql()));
                Hyperlink runLink = new Hyperlink("Run");
                runLink.setOnAction(e -> run.accept(example.sql()));
                HBox heading = new HBox(10, name, runLink, useLink);
                heading.setAlignment(Pos.BASELINE_LEFT);
                box.getChildren().add(new VBox(3, heading, explanation, sql));
            }
            content.getChildren().add(box);
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("cheat-scroll");
        return scroll;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("home-muted");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}

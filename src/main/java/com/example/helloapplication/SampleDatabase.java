package com.example.helloapplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Random;

public final class SampleDatabase {

    public static final Path DEFAULT_FILE = AppPaths.DATA_DIR.resolve("sample.db");

    private static final long SEED = 20260922L;
    private static final int ORDER_COUNT = 1200;

    private static final String[] FIRST_NAMES = {
            "Ada", "Grace", "Alan", "Edsger", "Barbara", "Donald", "Margaret", "Ken", "Frances", "John",
            "Radia", "Tim", "Hedy", "Dennis", "Katherine", "Linus", "Shafi", "Guido", "Anita", "Bjarne"};
    private static final String[] LAST_NAMES = {
            "Lovelace", "Hopper", "Turing", "Dijkstra", "Liskov", "Knuth", "Hamilton", "Thompson", "Allen",
            "Backus", "Perlman", "Berners-Lee", "Lamarr", "Ritchie", "Johnson", "Torvalds", "Goldwasser",
            "van Rossum", "Borg", "Stroustrup"};
    private static final String[] CITIES = {
            "London", "Arlington", "Manchester", "Austin", "Boston", "Stanford", "Toronto", "Dublin",
            "Melbourne", "Oslo", "Lisbon", "Denver"};
    private static final String[][] SUPPLIERS = {
            {"Paper & Quill Co.", "Leeds"}, {"Northwind Stationery", "Seattle"},
            {"Graphite Works", "Cumbria"}, {"Blue Ridge Office Supply", "Asheville"}};
    private static final Object[][] PRODUCTS = {
            {"PAD-A4", "Graph paper pad, A4", "Paper", 4.50, 1, 120},
            {"PAD-A5", "Dot grid notebook, A5", "Paper", 6.25, 1, 85},
            {"IDX-100", "Index cards (100)", "Paper", 3.00, 2, 0},
            {"PEN-GEL", "Gel pen, black", "Pens", 1.75, 2, 400},
            {"PEN-FNT", "Fountain pen", "Pens", 24.00, 3, 18},
            {"PCL-MEC", "Mechanical pencil 0.5mm", "Pencils", 2.25, 3, 210},
            {"PCL-HB12", "HB pencils (12)", "Pencils", 5.40, 3, 64},
            {"ERS-VNL", "Vinyl eraser", "Pencils", 0.90, 3, 300},
            {"RUL-30", "Steel ruler, 30cm", "Tools", 3.80, 4, 45},
            {"CMP-PRO", "Drafting compass", "Tools", 12.50, 4, 12},
            {"STP-STD", "Stapler", "Tools", 8.99, 4, 30},
            {"FLD-ARCH", "Archive folders (10)", "Filing", 7.20, 2, 55},
            {"BND-RING", "Ring binder", "Filing", 4.10, 2, 70},
            {"LBL-ROLL", "Label roll", "Filing", 9.60, 1, null},
    };
    private static final String[] STATUSES = {"delivered", "delivered", "delivered", "shipped", "processing", "cancelled"};

    private SampleDatabase() {
    }

    public static Path ensure() throws SQLException {
        return ensure(DEFAULT_FILE);
    }

    public static Path ensure(Path file) throws SQLException {
        if (Files.exists(file)) {
            return file;
        }
        Path partial = file.resolveSibling(file.getFileName() + ".partial");
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.deleteIfExists(partial);
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + partial.toAbsolutePath())) {
                conn.setAutoCommit(false);
                createSchema(conn);
                fill(conn, new Random(SEED));
                conn.commit();
            }
            Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Could not create the sample database at " + file, e);
        }
        return file;
    }

    private static void createSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE customers (
                        customer_id  INTEGER PRIMARY KEY,
                        first_name   TEXT NOT NULL,
                        last_name    TEXT NOT NULL,
                        email        TEXT NOT NULL UNIQUE,
                        city         TEXT,
                        joined_on    TEXT NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE suppliers (
                        supplier_id  INTEGER PRIMARY KEY,
                        name         TEXT NOT NULL,
                        city         TEXT
                    )""");
            st.execute("""
                    CREATE TABLE products (
                        product_id   INTEGER PRIMARY KEY,
                        sku          TEXT NOT NULL UNIQUE,
                        name         TEXT NOT NULL,
                        category     TEXT NOT NULL,
                        unit_price   REAL NOT NULL,
                        supplier_id  INTEGER REFERENCES suppliers(supplier_id),
                        in_stock     INTEGER
                    )""");
            st.execute("""
                    CREATE TABLE orders (
                        order_id     INTEGER PRIMARY KEY,
                        customer_id  INTEGER NOT NULL REFERENCES customers(customer_id),
                        placed_on    TEXT NOT NULL,
                        status       TEXT NOT NULL,
                        shipped_on   TEXT
                    )""");
            st.execute("""
                    CREATE TABLE order_items (
                        order_id     INTEGER NOT NULL REFERENCES orders(order_id),
                        product_id   INTEGER NOT NULL REFERENCES products(product_id),
                        quantity     INTEGER NOT NULL,
                        unit_price   REAL NOT NULL,
                        PRIMARY KEY (order_id, product_id)
                    )""");
        }
    }

    private static void fill(Connection conn, Random random) throws SQLException {
        int customerCount = FIRST_NAMES.length * 2;
        LocalDate start = LocalDate.of(2024, 1, 1);

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO customers VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int id = 1; id <= customerCount; id++) {
                String first = FIRST_NAMES[(id - 1) % FIRST_NAMES.length];
                String last = LAST_NAMES[(id * 7) % LAST_NAMES.length];
                ps.setInt(1, id);
                ps.setString(2, first);
                ps.setString(3, last);
                ps.setString(4, (first + "." + last).toLowerCase().replace(" ", "") + id + "@example.com");
                ps.setString(5, random.nextInt(8) == 0 ? null : CITIES[random.nextInt(CITIES.length)]);
                ps.setString(6, start.plusDays(random.nextInt(365)).toString());
                ps.executeUpdate();
            }
        }

        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO suppliers VALUES (?, ?, ?)")) {
            for (int i = 0; i < SUPPLIERS.length; i++) {
                ps.setInt(1, i + 1);
                ps.setString(2, SUPPLIERS[i][0]);
                ps.setString(3, SUPPLIERS[i][1]);
                ps.executeUpdate();
            }
        }

        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO products VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < PRODUCTS.length; i++) {
                Object[] p = PRODUCTS[i];
                ps.setInt(1, i + 1);
                ps.setString(2, (String) p[0]);
                ps.setString(3, (String) p[1]);
                ps.setString(4, (String) p[2]);
                ps.setDouble(5, (Double) p[3]);
                ps.setInt(6, (Integer) p[4]);
                ps.setObject(7, p[5]);
                ps.executeUpdate();
            }
        }

        try (PreparedStatement order = conn.prepareStatement("INSERT INTO orders VALUES (?, ?, ?, ?, ?)");
             PreparedStatement item = conn.prepareStatement("INSERT INTO order_items VALUES (?, ?, ?, ?)")) {
            for (int id = 1; id <= ORDER_COUNT; id++) {
                LocalDate placed = start.plusDays(30 + (long) id * 600 / ORDER_COUNT);
                String status = STATUSES[random.nextInt(STATUSES.length)];
                boolean shipped = status.equals("delivered") || status.equals("shipped");
                order.setInt(1, id);
                order.setInt(2, 1 + random.nextInt(customerCount));
                order.setString(3, placed.toString());
                order.setString(4, status);
                order.setString(5, shipped ? placed.plusDays(1 + random.nextInt(5)).toString() : null);
                order.executeUpdate();

                int lines = 1 + random.nextInt(4);
                int firstProduct = random.nextInt(PRODUCTS.length);
                for (int line = 0; line < lines; line++) {
                    int product = (firstProduct + line * 3) % PRODUCTS.length;
                    item.setInt(1, id);
                    item.setInt(2, product + 1);
                    item.setInt(3, 1 + random.nextInt(6));
                    item.setDouble(4, (Double) PRODUCTS[product][3]);
                    item.executeUpdate();
                }
            }
        }
    }
}

package com.example.helloapplication;

import com.example.helloapplication.DatabaseBrowser.ForeignKey;
import com.example.helloapplication.DatabaseBrowser.TableRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinkEditorTest {

    @TempDir
    Path tempDir;

    private Path dataset;

    private static final ForeignKey CUST_NO = new ForeignKey(new TableRef(null, "orders"), "cust_no",
            new TableRef(null, "customers"), "customer_id");

    @BeforeEach
    void importFilesWithDifferentNames() throws Exception {
        Path customers = tempDir.resolve("customers.csv");
        Files.writeString(customers, "customer_id,name,city\n1,Ada,Boston\n2,Grace,Boston\n3,Alan,Denver\n");
        Path orders = tempDir.resolve("orders.csv");
        Files.writeString(orders, "order_id,cust_no,total\n10,1,9.5\n11,2,20\n12,2,3.25\n13,9,4\n14,,1\n");
        dataset = DataImporter.importFiles(List.of(customers, orders), tempDir.resolve("imports")).database();
    }

    private void execute(String... sql) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dataset.toAbsolutePath());
             Statement st = conn.createStatement()) {
            for (String s : sql) {
                st.execute(s);
            }
        }
    }

    private List<String> column(String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dataset.toAbsolutePath());
             Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    @Test
    void differentlyNamedColumnsAreNotLinkedOnImport() throws Exception {
        assertEquals(List.of(), DatabaseBrowser.sqlite(dataset).listForeignKeys());
    }

    @Test
    void checkCountsHowManyValuesMatch() throws Exception {
        LinkEditor.Check check = LinkEditor.check(DatabaseBrowser.sqlite(dataset), CUST_NO);

        assertEquals(3, check.distinct()); // 1, 2 and 9; the empty one doesn't count
        assertEquals(2, check.matched());
        assertTrue(check.canSave());
        assertEquals("2 of 3 distinct cust_no values (66%) are in customers.customer_id. "
                + "The rest will show as rows that point at nothing.", LinkEditor.describeCheck(CUST_NO, check));
    }

    @Test
    void aColumnWithRepeatsCantBePointedAt() throws Exception {
        ForeignKey toCity = new ForeignKey(new TableRef(null, "orders"), "cust_no",
                new TableRef(null, "customers"), "city");

        LinkEditor.Check check = LinkEditor.check(DatabaseBrowser.sqlite(dataset), toCity);

        assertTrue(check.parentRepeats());
        assertFalse(check.canSave());
        assertTrue(LinkEditor.describeCheck(toCity, check).startsWith("customers.city has repeated values"));
    }

    @Test
    void savedLinkIsARealForeignKeyAndKeepsRowsIndexesAndViews() throws Exception {
        execute("CREATE INDEX orders_by_total ON orders (total)",
                "CREATE VIEW big_orders AS SELECT * FROM orders WHERE total > 5");
        List<String> before = column("SELECT order_id || ':' || coalesce(cust_no, '') || ':' || total FROM orders");
        List<String> messages = new ArrayList<>();

        LinkEditor.addLink(dataset, CUST_NO, messages::add);

        DatabaseBrowser browser = DatabaseBrowser.sqlite(dataset);
        assertEquals(List.of(CUST_NO), browser.listForeignKeys());
        assertEquals(before, column("SELECT order_id || ':' || coalesce(cust_no, '') || ':' || total FROM orders"));
        assertEquals(List.of("orders_by_total"), column("SELECT name FROM sqlite_master WHERE type = 'index'"
                + " AND tbl_name = 'orders' AND sql IS NOT NULL"));
        assertEquals(List.of("10", "11"), column("SELECT order_id FROM big_orders ORDER BY order_id"));
        assertEquals(List.of("orders"), column("SELECT name FROM sqlite_master WHERE name LIKE '%orders'"
                + " AND type = 'table'"));
        assertEquals(1, browser.previewJoin(CUST_NO, 10).unmatchedRows()); // order 13 points at customer 9
        assertTrue(messages.stream().anyMatch(m -> m.contains("rebuilding orders")), messages.toString());
    }

    @Test
    void aParentColumnThatIsntAKeyYetGetsAUniqueIndex() throws Exception {
        ForeignKey toName = new ForeignKey(new TableRef(null, "orders"), "cust_no",
                new TableRef(null, "customers"), "name");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dataset.toAbsolutePath())) {
            assertFalse(LinkEditor.isKey(conn, toName.parent(), "name"));
        }

        LinkEditor.addLink(dataset, toName, DataImporter.Progress.NONE);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dataset.toAbsolutePath())) {
            assertTrue(LinkEditor.isKey(conn, toName.parent(), "name"));
        }
        assertEquals(List.of(toName), DatabaseBrowser.sqlite(dataset).listForeignKeys());
    }

    @Test
    void aColumnThatAlreadyHasALinkIsReported() throws Exception {
        LinkEditor.addLink(dataset, CUST_NO, DataImporter.Progress.NONE);

        LinkEditor.Check check = LinkEditor.check(DatabaseBrowser.sqlite(dataset), CUST_NO);

        assertFalse(check.canSave());
        assertEquals("cust_no is already linked: orders.cust_no → customers.customer_id.",
                LinkEditor.describeCheck(CUST_NO, check));
    }

    @Test
    void theForeignKeyGoesAtTheEndOfTheDefinitionList() throws Exception {
        String sql = "CREATE TABLE \"odd (name)\" (\n    \"a\" TEXT PRIMARY KEY CHECK (a <> ')'),\n"
                + "    [b)] INTEGER -- note (\n) WITHOUT ROWID";

        String result = LinkEditor.withForeignKey(LinkEditor.renamed(sql, "\"new\""),
                "FOREIGN KEY (\"a\") REFERENCES \"p\" (\"id\")");

        assertEquals("CREATE TABLE \"new\" (\n    \"a\" TEXT PRIMARY KEY CHECK (a <> ')'),\n"
                + "    [b)] INTEGER -- note (\n,\n    FOREIGN KEY (\"a\") REFERENCES \"p\" (\"id\")\n) WITHOUT ROWID", result);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = conn.createStatement()) {
            st.execute(sql);
            st.execute(result); // still valid SQL: the comma didn't end up inside the comment
        }
    }
}

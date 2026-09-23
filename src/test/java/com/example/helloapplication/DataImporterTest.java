package com.example.helloapplication;

import com.example.helloapplication.DataImporter.ImportResult;
import com.example.helloapplication.DatabaseBrowser.ForeignKey;
import com.example.helloapplication.DatabaseBrowser.JoinPreview;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataImporterTest {

    @TempDir
    Path tempDir;

    private Path customers;
    private Path orders;
    private Path products;
    private Path sales;

    @BeforeEach
    void writeCsvFiles() throws Exception {
        customers = tempDir.resolve("Customers.csv");
        Files.writeString(customers, "customer_id,name,zip\n1,Ada,02139\n2,Grace,10001\n3,Alan,\n");

        orders = tempDir.resolve("orders.csv");
        Files.writeString(orders, "order_id,customer_id,total\n10,1,9.5\n11,2,20\n12,2,3.25\n13,,4\n");

        StringBuilder p = new StringBuilder("sku,title\n");
        StringBuilder s = new StringBuilder("sale_id,item_code\n");
        for (int i = 0; i < 12; i++) {
            p.append("SKU-").append(i).append(",item ").append(i).append('\n');
            s.append(i).append(",SKU-").append(i).append('\n');
        }
        products = tempDir.resolve("products.csv");
        Files.writeString(products, p);
        sales = tempDir.resolve("sales.csv");
        Files.writeString(sales, s);
    }

    private ImportResult importAll() throws Exception {
        return DataImporter.importFiles(List.of(customers, orders, products, sales), tempDir.resolve("imports"));
    }

    @Test
    void createsOneTablePerFileInANewDatabase() throws Exception {
        ImportResult result = importAll();

        assertTrue(Files.exists(result.database()));
        assertEquals("customers-orders-and-2-more.db", result.database().getFileName().toString());
        assertEquals(List.of("customers", "orders", "products", "sales"), result.tables());
        assertEquals(List.of("customers", "orders", "products", "sales"),
                DatabaseBrowser.sqlite(result.database()).listTables().stream().map(TableRef::displayName).toList());
        assertEquals(4, DatabaseBrowser.sqlite(result.database()).preview(new TableRef(null, "orders"), 10).totalRows());
        try (var files = Files.list(tempDir.resolve("imports"))) {
            assertEquals(1, files.count(), "no .partial file left behind");
        }
    }

    @Test
    void confidentLinksBecomeForeignKeysAndValuesOnlyOnesDoNot() throws Exception {
        ImportResult result = importAll();

        assertEquals(1, result.saved().size());
        assertEquals("orders", result.saved().get(0).childTable());
        assertEquals(1, result.notSaved().size());
        assertEquals("sales", result.notSaved().get(0).childTable());

        assertEquals(List.of(new ForeignKey(new TableRef(null, "orders"), "customer_id",
                        new TableRef(null, "customers"), "customer_id")),
                DatabaseBrowser.sqlite(result.database()).listForeignKeys());
    }

    @Test
    void typesAreInferredAndLeadingZerosKept() throws Exception {
        ImportResult result = importAll();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + result.database().toAbsolutePath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT typeof(customer_id), typeof(zip), zip FROM customers WHERE name = 'Ada'")) {
            assertTrue(rs.next());
            assertEquals("integer", rs.getString(1));
            assertEquals("text", rs.getString(2));
            assertEquals("02139", rs.getString(3));
        }
    }

    @Test
    void joinPreviewShowsParentColumnsAndCountsOrphans() throws Exception {
        ImportResult result = importAll();
        DatabaseBrowser browser = DatabaseBrowser.sqlite(result.database());
        ForeignKey key = browser.listForeignKeys().get(0);

        JoinPreview join = browser.previewJoin(key, 10);

        assertEquals(List.of("orders.order_id", "orders.customer_id", "orders.total",
                "customers.customer_id", "customers.name", "customers.zip"), join.preview().columns());
        assertEquals(4, join.preview().totalRows());
        assertEquals(Arrays.asList("10", "1", "9.5", "1", "Ada", "02139"), join.preview().rows().get(0));
        // Order 13 has no customer_id, so it isn't counted as unmatched: NULL means "no link", not a broken one.
        assertEquals(0, join.unmatchedRows());
    }

    @Test
    void joinPreviewRejectsUnknownRelationships() throws Exception {
        DatabaseBrowser browser = DatabaseBrowser.sqlite(importAll().database());
        ForeignKey made_up = new ForeignKey(new TableRef(null, "sales"), "item_code",
                new TableRef(null, "products"), "sku");

        assertThrows(IllegalArgumentException.class, () -> browser.previewJoin(made_up, 10));
    }

    @Test
    void sameTableNameFromTwoFilesIsMadeUnique() {
        assertEquals(List.of("orders", "Orders_2"), DataImporter.uniqueNames(List.of("orders", "Orders")));
    }

    @Test
    void importingTheSameFilesAgainMakesASecondDataset() throws Exception {
        Path first = DataImporter.importFiles(List.of(customers), tempDir.resolve("imports")).database();
        Path second = DataImporter.importFiles(List.of(customers), tempDir.resolve("imports")).database();

        assertEquals("customers.db", first.getFileName().toString());
        assertEquals("customers-2.db", second.getFileName().toString());
    }

    @Test
    void jsonAndCsvFilesImportTogetherAndLinkUp() throws Exception {
        Path json = tempDir.resolve("payments.json");
        Files.writeString(json, """
                [
                  {"payment_id": 1, "order_id": 10, "amount": 9.5, "card": {"brand": "visa", "last4": "0042"}},
                  {"payment_id": 2, "order_id": 11, "amount": 20, "refunded": true},
                  {"payment_id": 3, "order_id": 12, "amount": 3.25, "tags": ["gift", "rush"]}
                ]""");

        ImportResult result = DataImporter.importFiles(List.of(customers, orders, json), tempDir.resolve("imports"));

        assertEquals(List.of("customers", "orders", "payments"), result.tables());
        assertEquals("customers-orders-payments.db", result.database().getFileName().toString());
        List<String> links = result.saved().stream()
                .map(r -> r.childTable() + "." + r.childColumn() + " -> " + r.parentTable() + "." + r.parentColumn())
                .toList();
        assertTrue(links.contains("payments.order_id -> orders.order_id"), links.toString());

        DatabaseBrowser browser = DatabaseBrowser.sqlite(result.database());
        assertEquals(List.of("payment_id", "order_id", "amount", "card_brand", "card_last4", "refunded", "tags"),
                browser.listColumns(new TableRef(null, "payments")));
        var rows = browser.query("SELECT amount, card_last4, typeof(amount), tags FROM payments ORDER BY payment_id", 10)
                .rows();
        assertEquals(Arrays.asList("9.5", "0042", "real", "NULL"), rows.get(0));
        assertEquals("[\"gift\",\"rush\"]", rows.get(2).get(3));
    }

    @Test
    void canImportCsvTsvAndJsonFiles() {
        assertTrue(DataImporter.canImport(Path.of("a.CSV")));
        assertTrue(DataImporter.canImport(Path.of("a.tsv")));
        assertTrue(DataImporter.canImport(Path.of("a.json")));
        assertTrue(DataImporter.canImport(Path.of("a.jsonl")));
        assertTrue(DataImporter.canImport(Path.of("a.ndjson")));
        assertFalse(DataImporter.canImport(Path.of("a.db")));
        assertFalse(DataImporter.canImport(Path.of("a.xlsx")));
    }

    @Test
    void datasetNameComesFromTheTables() {
        assertEquals("sales", DataImporter.datasetName(List.of("sales")));
        assertEquals("a-b-c", DataImporter.datasetName(List.of("a", "b", "c")));
        assertEquals("a-b-and-3-more", DataImporter.datasetName(List.of("a", "b", "c", "d", "e")));
    }

    @Test
    void sampleDatabaseForeignKeysAreListed() throws Exception {
        Path sample = SampleDatabase.ensure(tempDir.resolve("sample.db"));

        List<String> keys = DatabaseBrowser.sqlite(sample).listForeignKeys().stream()
                .map(k -> k.child().name() + "." + k.childColumn() + " -> " + k.parent().name() + "." + k.parentColumn())
                .toList();
        assertEquals(List.of(
                "order_items.order_id -> orders.order_id",
                "order_items.product_id -> products.product_id",
                "orders.customer_id -> customers.customer_id",
                "products.supplier_id -> suppliers.supplier_id"), keys);
    }

    @Test
    void tablesBiggerThanTheSampleAreCheckedInFull() throws Exception {
        int rows = StagedTable.SAMPLE_ROWS * 3;
        StringBuilder c = new StringBuilder("customer_id,code\n");
        StringBuilder o = new StringBuilder("order_id,customer_id\n");
        for (int i = 1; i <= rows; i++) {
            // code repeats only after the sampled rows, so the sample alone would call it a key.
            c.append(i).append(",C").append(i <= StagedTable.SAMPLE_ROWS ? i : i - StagedTable.SAMPLE_ROWS).append('\n');
            o.append(i).append(',').append((i % rows) + 1).append('\n');
        }
        Path bigCustomers = tempDir.resolve("customers.csv");
        Files.writeString(bigCustomers, c);
        Path bigOrders = tempDir.resolve("orders.csv");
        Files.writeString(bigOrders, o);
        List<String> messages = new java.util.ArrayList<>();

        ImportResult result = DataImporter.importFiles(List.of(bigCustomers, bigOrders), tempDir.resolve("imports"),
                messages::add);

        assertEquals(List.of(new ForeignKey(new TableRef(null, "orders"), "customer_id",
                        new TableRef(null, "customers"), "customer_id")),
                DatabaseBrowser.sqlite(result.database()).listForeignKeys());
        assertEquals(rows, result.saved().get(0).distinctValues());
        assertEquals(rows, DatabaseBrowser.sqlite(result.database()).preview(new TableRef(null, "orders"), 1).totalRows());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Saving orders")), messages.toString());
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + result.database().toAbsolutePath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE name = 'customers'")) {
            assertTrue(rs.next());
            assertFalse(rs.getString(1).contains("UNIQUE"), "code repeats, so it isn't a key: " + rs.getString(1));
        }
        try (var files = Files.list(tempDir.resolve("imports"))) {
            assertEquals(1, files.count(), "no scratch files left behind");
        }
    }

    @Test
    void aFailedImportLeavesNothingBehind() throws Exception {
        Path broken = tempDir.resolve("broken.csv");
        Files.writeString(broken, "id,note\n1,\"never closed\n2,x\n");

        assertThrows(java.io.IOException.class,
                () -> DataImporter.importFiles(List.of(customers, broken), tempDir.resolve("imports")));
        try (var files = Files.list(tempDir.resolve("imports"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void readingMessageShowsProgressThroughTheFile() {
        assertEquals("Reading orders.csv (3.4 GB): 25%, 2,000,000 rows so far…",
                DataImporter.readingMessage("orders.csv", 912_680_550L, 3_650_722_200L, 2_000_000));
    }
}

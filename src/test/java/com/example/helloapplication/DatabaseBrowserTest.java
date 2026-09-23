package com.example.helloapplication;

import com.example.helloapplication.DatabaseBrowser.TableRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBrowserTest {

    @TempDir
    Path tempDir;

    private Path dbFile;

    @BeforeEach
    void createFixture() throws Exception {
        dbFile = tempDir.resolve("shop.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE orders (order_id INTEGER PRIMARY KEY, customer TEXT, total REAL, receipt BLOB)");
            st.execute("CREATE TABLE \"odd \"\"name\"\" table\" (x INTEGER)");
            st.execute("INSERT INTO \"odd \"\"name\"\" table\" VALUES (7)");
            st.execute("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
            st.execute("INSERT INTO customers (name) VALUES ('Ada')");

            conn.setAutoCommit(false); // one commit for all 250 rows, not 250
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO orders VALUES (?, ?, ?, ?)")) {
                for (int i = 1; i <= 250; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, i == 1 ? null : "customer " + i);
                    ps.setDouble(3, i * 1.5);
                    ps.setBytes(4, i == 1 ? new byte[]{1, 2, 3} : null);
                    ps.executeUpdate();
                }
            }
            conn.commit();
        }
    }

    private static TableRef table(String name) {
        return new TableRef(null, name);
    }

    // ---- tables ----

    @Test
    void listTablesIsSortedAndSkipsSqliteInternals() throws Exception {
        List<TableRef> tables = DatabaseBrowser.sqlite(dbFile).listTables();

        // customers' AUTOINCREMENT creates sqlite_sequence, which must not show up.
        assertEquals(List.of(table("customers"), table("odd \"name\" table"), table("orders")), tables);
    }

    @Test
    void previewCapsRowsButReportsTheTotal() throws Exception {
        DatabaseBrowser.TablePreview preview = DatabaseBrowser.sqlite(dbFile).preview(table("orders"), 200);

        assertEquals(List.of("order_id", "customer", "total", "receipt"), preview.columns());
        assertEquals(200, preview.rows().size());
        assertEquals(250, preview.totalRows());
    }

    @Test
    void previewRendersNullAndBlobCells() throws Exception {
        List<String> first = DatabaseBrowser.sqlite(dbFile).preview(table("orders"), 1).rows().get(0);

        assertEquals("1", first.get(0));
        assertEquals("NULL", first.get(1));
        assertEquals("BLOB (3 bytes)", first.get(3));
    }

    @Test
    void previewHandlesNamesThatNeedQuoting() throws Exception {
        DatabaseBrowser.TablePreview preview =
                DatabaseBrowser.sqlite(dbFile).preview(table("odd \"name\" table"), 10);

        assertEquals(List.of(List.of("7")), preview.rows());
        assertEquals(1, preview.totalRows());
    }

    @Test
    void previewRejectsUnknownTables() {
        DatabaseBrowser browser = DatabaseBrowser.sqlite(dbFile);

        assertThrows(IllegalArgumentException.class,
                () -> browser.preview(table("orders; DROP TABLE orders"), 10));
    }

    @Test
    void nonSqliteFileFails() throws Exception {
        Path notes = tempDir.resolve("notes.txt");
        Files.writeString(notes, "not a database, just some text that is long enough to have a header".repeat(4));

        assertThrows(SQLException.class, () -> DatabaseBrowser.sqlite(notes).listTables());
    }

    @Test
    void missingFileFailsAndIsNotCreated() {
        Path missing = tempDir.resolve("missing.db");

        assertThrows(SQLException.class, () -> DatabaseBrowser.sqlite(missing).listTables());
        assertFalse(Files.exists(missing), "read-only open must not create the file");
    }

    @Test
    void connectionIsReadOnly() throws Exception {
        try (Connection conn = DatabaseBrowser.sqlite(dbFile).openConnection();
             Statement st = conn.createStatement()) {
            assertThrows(SQLException.class, () -> st.execute("DELETE FROM orders"));
        }
        assertEquals(250, DatabaseBrowser.sqlite(dbFile).preview(table("orders"), 1).totalRows());
    }

    @Test
    void sqliteDescriptionIsTheFileName() {
        assertEquals("shop.db", DatabaseBrowser.sqlite(dbFile).description());
        assertEquals(dbFile, DatabaseBrowser.sqlite(dbFile).file());
    }

    @Test
    void listColumnsIsInTableOrder() throws Exception {
        assertEquals(List.of("order_id", "customer", "total", "receipt"),
                DatabaseBrowser.sqlite(dbFile).listColumns(table("orders")));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseBrowser.sqlite(dbFile).listColumns(table("nope")));
    }

    @Test
    void quotingUsesTheDatabasesQuoteCharacter() {
        assertEquals("`my``table`", DatabaseBrowser.quoteIdentifier("my`table", "`"));
        assertEquals("\"sales\".\"q\"\"1\"",
                DatabaseBrowser.qualifiedName(new TableRef("sales", "q\"1"), "\""));
        assertEquals("sales.orders", new TableRef("sales", "orders").displayName());
        assertEquals("orders", table("orders").displayName());
    }

    // ---- queries typed by the user ----

    @Test
    void queryCapsRowsButCountsTheWholeResult() throws Exception {
        DatabaseBrowser.TablePreview result = DatabaseBrowser.sqlite(dbFile)
                .query("SELECT order_id, total * 2 AS doubled FROM orders WHERE order_id > 10 ORDER BY order_id", 5);

        assertEquals(List.of("order_id", "doubled"), result.columns());
        assertEquals(5, result.rows().size());
        assertEquals(240, result.totalRows());
        assertEquals(List.of("11", "33.0"), result.rows().get(0));
    }

    @Test
    void queryAcceptsWithClausesCommentsAndATrailingSemicolon() throws Exception {
        DatabaseBrowser browser = DatabaseBrowser.sqlite(dbFile);

        assertEquals(1, browser.query("WITH big AS (SELECT * FROM orders WHERE total > 300)\n"
                + "SELECT COUNT(*) AS n FROM big;", 10).rows().size());
        assertEquals(List.of(List.of("Ada")), browser.query("-- who signed up\n"
                + "SELECT name FROM customers -- just one\n;  ", 10).rows());
        assertEquals(List.of(List.of("a;b")), browser.query("select 'a;b' as x", 10).rows());
    }

    @Test
    void checkQueryRefusesAnythingButOneSelect() {
        assertEquals("Only SELECT queries can be run here; the data can't be changed.",
                assertThrows(IllegalArgumentException.class,
                        () -> DatabaseBrowser.checkQuery("DELETE FROM orders")).getMessage());
        assertEquals("Run one query at a time.",
                assertThrows(IllegalArgumentException.class,
                        () -> DatabaseBrowser.checkQuery("SELECT 1; DROP TABLE orders")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> DatabaseBrowser.checkQuery("   ;  "));
        assertThrows(IllegalArgumentException.class, () -> DatabaseBrowser.checkQuery(null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseBrowser.checkQuery("PRAGMA writable_schema = 1"));
        assertEquals("SELECT 1", DatabaseBrowser.checkQuery("  /* note */ SELECT 1 ;; -- done"));
        assertEquals("select*from orders", DatabaseBrowser.checkQuery("select*from orders"));
    }

    @Test
    void queryCannotChangeTheData() throws Exception {
        DatabaseBrowser browser = DatabaseBrowser.sqlite(dbFile);

        // WITH ... DELETE is valid SQLite; it gets past the first-word check but can't run.
        assertThrows(SQLException.class,
                () -> browser.query("WITH x AS (SELECT 1) DELETE FROM orders", 10));
        assertEquals(250, browser.preview(table("orders"), 1).totalRows());
    }

    @Test
    void queryErrorsComeFromSqlite() {
        SQLException e = assertThrows(SQLException.class,
                () -> DatabaseBrowser.sqlite(dbFile).query("SELECT nme FROM customers", 10));
        assertTrue(e.getMessage().contains("no such column: nme"), e.getMessage());
    }
}

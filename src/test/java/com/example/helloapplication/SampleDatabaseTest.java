package com.example.helloapplication;

import com.example.helloapplication.DatabaseBrowser.TableRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SampleDatabaseTest {

    @TempDir
    Path tempDir;

    @Test
    void createsTheShopTablesWithData() throws Exception {
        Path file = SampleDatabase.ensure(tempDir.resolve("data").resolve("sample.db"));
        DatabaseBrowser browser = DatabaseBrowser.sqlite(file);

        assertEquals(List.of("customers", "order_items", "orders", "products", "suppliers"),
                browser.listTables().stream().map(TableRef::displayName).toList());
        assertEquals(1200, browser.preview(new TableRef(null, "orders"), 1).totalRows());
        assertEquals(40, browser.preview(new TableRef(null, "customers"), 1).totalRows());
        assertFalse(Files.exists(file.resolveSibling("sample.db.partial")), "temporary build file is cleaned up");
    }

    @Test
    void existingFileIsReusedNotRebuilt() throws Exception {
        Path file = SampleDatabase.ensure(tempDir.resolve("sample.db"));
        long firstModified = Files.getLastModifiedTime(file).toMillis();

        assertEquals(file, SampleDatabase.ensure(file));
        assertEquals(firstModified, Files.getLastModifiedTime(file).toMillis());
    }

    @Test
    void sameSeedGivesTheSameData() throws Exception {
        Path a = SampleDatabase.ensure(tempDir.resolve("a.db"));
        Path b = SampleDatabase.ensure(tempDir.resolve("b.db"));
        TableRef orders = new TableRef(null, "orders");

        assertEquals(DatabaseBrowser.sqlite(a).preview(orders, 50).rows(),
                DatabaseBrowser.sqlite(b).preview(orders, 50).rows());
    }
}

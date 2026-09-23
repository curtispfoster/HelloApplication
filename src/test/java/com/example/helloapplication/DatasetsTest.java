package com.example.helloapplication;

import com.example.helloapplication.Datasets.Dataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatasetsTest {

    @TempDir
    Path tempDir;

    @Test
    void listsDatabaseFilesNewestFirst() throws Exception {
        Path older = Files.writeString(tempDir.resolve("customers.db"), "");
        Path newer = Files.writeString(tempDir.resolve("orders-payments.sqlite"), "");
        Files.writeString(tempDir.resolve("orders.db.partial"), "");
        Files.writeString(tempDir.resolve("notes.txt"), "");
        Files.createDirectory(tempDir.resolve("folder.db"));
        Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-09-01T10:00:00Z")));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-09-20T10:00:00Z")));

        List<Dataset> datasets = Datasets.list(tempDir);

        assertEquals(List.of("orders, payments", "customers"), datasets.stream().map(Dataset::name).toList());
        assertEquals(newer, datasets.get(0).file());
    }

    @Test
    void missingFolderMeansNoDatasets() throws Exception {
        assertEquals(List.of(), Datasets.list(tempDir.resolve("not-there")));
    }

    @Test
    void namesAreReadableForPeople() {
        assertEquals("customers", Datasets.displayName(Path.of("customers.db")));
        assertEquals("customers, orders, products", Datasets.displayName(Path.of("customers-orders-products.db")));
        assertEquals("customers, orders, and 3 more", Datasets.displayName(Path.of("customers-orders-and-3-more.db")));
        assertEquals("customers (2)", Datasets.displayName(Path.of("customers-2.db")));
        assertEquals("customers, orders, and 3 more (2)",
                Datasets.displayName(Path.of("customers-orders-and-3-more-2.db")));
        assertEquals("Import of Sep 22, 2026 18:39", Datasets.displayName(Path.of("import-20260922-183919.db")));
        assertEquals("Import of Sep 22, 2026 18:39", Datasets.displayName(Path.of("import-20260922-183919-2.db")));
    }
}

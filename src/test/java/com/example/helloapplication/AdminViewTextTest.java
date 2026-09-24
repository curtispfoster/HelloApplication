package com.example.helloapplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminViewTextTest {

    @TempDir
    Path tempDir;

    @Test
    void joinSummaryNamesTheColumnsAndOrphans() {
        var orders = new DatabaseBrowser.TableRef(null, "orders");
        var customers = new DatabaseBrowser.TableRef(null, "customers");
        var sameName = new DatabaseBrowser.ForeignKey(orders, "customer_id", customers, "customer_id");
        var differentName = new DatabaseBrowser.ForeignKey(orders, "customer_id", customers, "id");

        assertEquals("On customer_id. Showing 200 of 1,200 rows.", Admin_View.describeJoin(sameName, 200, 1200, 0));
        assertEquals("On customer_id = id. All 3 rows. 1 row points at a customers row that isn't there.",
                Admin_View.describeJoin(differentName, 3, 3, 1));
        assertEquals("On customer_id. All 5 rows. 2 rows point at a customers row that isn't there.",
                Admin_View.describeJoin(sameName, 5, 5, 2));
    }

    @Test
    void importSummaryCountsTablesAndLinks() {
        var strong = new RelationshipFinder.Relationship("orders", "customer_id", "customers", "customer_id",
                3, 3, RelationshipFinder.Confidence.STRONG);
        var possible = new RelationshipFinder.Relationship("sales", "item_code", "products", "sku",
                12, 12, RelationshipFinder.Confidence.POSSIBLE);
        var db = Path.of("data", "imports", "customers-orders-sales.db");

        assertEquals("Imported 3 tables into customers-orders-sales.db, now a dataset users can see. "
                        + "Saved 1 link, and found 1 possible link that wasn't saved.",
                Admin_View.describeImport(new DataImporter.ImportResult(db, List.of("a", "b", "c"),
                        List.of(strong), List.of(possible))));
        assertEquals("Imported 2 tables into customers-orders-sales.db, now a dataset users can see. "
                        + "No links between them were found.",
                Admin_View.describeImport(new DataImporter.ImportResult(db, List.of("a", "b"), List.of(), List.of())));
        assertEquals("Imported 1 table into customers-orders-sales.db, now a dataset users can see.",
                Admin_View.describeImport(new DataImporter.ImportResult(db, List.of("a"), List.of(), List.of())));
    }

    @Test
    void matchDetailRoundsDown() {
        var all = new RelationshipFinder.Relationship("a", "b", "c", "d", 10, 10, RelationshipFinder.Confidence.STRONG);
        var most = new RelationshipFinder.Relationship("a", "b", "c", "d", 199, 200, RelationshipFinder.Confidence.LIKELY);

        assertEquals("Every value matched", Admin_View.describeMatch(all));
        assertEquals("99% of values matched", Admin_View.describeMatch(most));
    }

    @Test
    void importFailureUsesTheReadersMessage() {
        assertEquals("Couldn't import the files. empty.csv is empty.",
                Admin_View.describeImportFailure(new IOException("empty.csv is empty.")));
    }

    @Test
    void deleteResultIsDescribedPerStatus() {
        assertEquals("Deleted bob.", Admin_View.describeDeleteResult(UserManagement.Status.OK, "bob"));
        assertEquals("You can't delete bob.",
                Admin_View.describeDeleteResult(UserManagement.Status.FORBIDDEN, "bob"));
        assertEquals("bob was already deleted.",
                Admin_View.describeDeleteResult(UserManagement.Status.NOT_FOUND, "bob"));
        assertEquals("Couldn't delete bob. Try again.",
                Admin_View.describeDeleteResult(UserManagement.Status.ERROR, "bob"));
    }

    @Test
    void resetOutcomeIsDescribedPerStatus() {
        assertEquals("Reset bob's password.", Admin_View.describeResetOutcome(UserManagement.Status.OK, "bob"));
        assertEquals("You can't reset bob's password.",
                Admin_View.describeResetOutcome(UserManagement.Status.FORBIDDEN, "bob"));
        assertEquals("bob was already deleted.",
                Admin_View.describeResetOutcome(UserManagement.Status.NOT_FOUND, "bob"));
        assertEquals("Couldn't reset bob's password. Try again.",
                Admin_View.describeResetOutcome(UserManagement.Status.ERROR, "bob"));
    }

    @Test
    void openedMessageSaysReadOnly() {
        assertEquals("Opened shop.db (read-only)",
                Admin_View.openedMessage(DatabaseBrowser.sqlite(Path.of("shop.db"))));
    }

    @Test
    void onlyFilesInTheImportsFolderAreDatasets() {
        assertTrue(Admin_View.isDataset(DataImporter.DEFAULT_DIRECTORY.resolve("shop.db")));
        assertTrue(Admin_View.isDataset(Path.of("data", "imports", "..", "imports", "shop.db")));
        assertFalse(Admin_View.isDataset(Path.of("data", "sample.db")));
        assertFalse(Admin_View.isDataset(DataImporter.DEFAULT_DIRECTORY.resolve("old").resolve("shop.db")));
    }

    @Test
    void droppedFilesAreSplitIntoDataAndDatabases() throws Exception {
        List<File> files = List.of(
                Files.writeString(tempDir.resolve("a.csv"), "x").toFile(),
                Files.writeString(tempDir.resolve("b.JSON"), "[]").toFile(),
                Files.writeString(tempDir.resolve("c.ndjson"), "{}").toFile(),
                Files.writeString(tempDir.resolve("d.db"), "").toFile(),
                Files.writeString(tempDir.resolve("notes.txt"), "").toFile(),
                Files.createDirectory(tempDir.resolve("folder.csv")).toFile());

        Admin_View.DroppedFiles dropped = Admin_View.DroppedFiles.of(files);

        assertEquals(List.of("a.csv", "b.JSON", "c.ndjson"),
                dropped.data().stream().map(p -> p.getFileName().toString()).toList());
        assertEquals(List.of(tempDir.resolve("d.db")), dropped.databases());
    }

    @Test
    void addingToTheImportListSaysWhatHappened() {
        assertEquals("Added 2 files to the import list (3 in all).",
                Admin_View.describeAdded(2, List.of(), 3));
        assertEquals("Added 1 file to the import list (2 in all). orders.csv is already in the list.",
                Admin_View.describeAdded(1, List.of("orders.csv"), 2));
        assertEquals("a.csv, b.csv are already in the list.",
                Admin_View.describeAdded(0, List.of("a.csv", "b.csv"), 2));
    }
}

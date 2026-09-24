package com.example.helloapplication;

import com.example.helloapplication.SqlCheatSheet.Example;
import com.example.helloapplication.SqlCheatSheet.Section;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SqlCheatSheetTest {

    @TempDir
    Path tempDir;

    @Test
    void everyExampleRunsOnTheSampleShopAndReturnsRows() throws Exception {
        DatabaseBrowser sample = DatabaseBrowser.sqlite(SampleDatabase.ensure(tempDir.resolve("sample.db")));
        int examples = 0;
        for (Section section : SqlCheatSheet.SECTIONS) {
            for (Example example : section.examples()) {
                // The same checked, read-only path Home uses, so an example can't pass here and fail there.
                var result = sample.query(example.sql(), 500);
                assertFalse(result.rows().isEmpty(), example.title() + " returned no rows: " + example.sql());
                examples++;
            }
        }
        assertTrue(examples >= 20, "the cheat sheet should cover the common cases");
    }
}

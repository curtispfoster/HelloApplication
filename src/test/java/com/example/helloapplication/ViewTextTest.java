package com.example.helloapplication;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ViewTextTest {

    @Test
    void rowCountSaysAllWhenEveryRowIsShown() {
        assertEquals("All 12 rows", ViewText.describeCount(12, 12));
        assertEquals("1 row", ViewText.describeCount(1, 1));
        assertEquals("No rows", ViewText.describeCount(0, 0));
    }

    @Test
    void rowCountSaysShowingWhenCapped() {
        assertEquals("Showing 200 of 1,532 rows", ViewText.describeCount(200, 1532));
    }

    @Test
    void pluralCountsWithThousandsSeparators() {
        assertEquals("1 file", ViewText.plural(1, "file"));
        assertEquals("1,200 files", ViewText.plural(1200, "file"));
    }

    @Test
    void failureNamesTheLikelyCause() {
        assertEquals("Couldn't open notes.txt. It isn't a SQLite database.",
                ViewText.describeFailure("open notes.txt",
                        new SQLException("[SQLITE_NOTADB] File opened that is not a database file (file is not a database)")));
        assertEquals("Couldn't read orders. The file may be locked by another program.",
                ViewText.describeFailure("read orders", new SQLException("[SQLITE_BUSY] The database file is locked")));
        assertEquals("Couldn't open shop.db.", ViewText.describeFailure("open shop.db", new SQLException("boom")));
        assertEquals("Couldn't open shop.db.", ViewText.describeFailure("open shop.db", null));
    }

    @Test
    void queryMistakesShowSqlitesExplanation() {
        assertEquals("Couldn't run the query. No such column: nme.",
                ViewText.describeFailure("run the query",
                        new SQLException("[SQLITE_ERROR] SQL error or missing database (no such column: nme)")));
        assertEquals("Couldn't run the query. Near \"FORM\": syntax error.",
                ViewText.describeFailure("run the query",
                        new SQLException("[SQLITE_ERROR] SQL error or missing database (near \"FORM\": syntax error)")));
    }

    @Test
    void refusedQueriesKeepTheirOwnMessage() {
        assertEquals("Run one query at a time.",
                ViewText.describeFailure("run the query", new IllegalArgumentException("Run one query at a time.")));
    }
}

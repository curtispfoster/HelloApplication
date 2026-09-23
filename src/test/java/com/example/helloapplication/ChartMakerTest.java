package com.example.helloapplication;

import com.example.helloapplication.ChartMaker.Kind;
import com.example.helloapplication.ChartMaker.Measure;
import com.example.helloapplication.ChartMaker.Spec;
import com.example.helloapplication.DatabaseBrowser.TablePreview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChartMakerTest {

    @TempDir
    Path tempDir;

    private DatabaseBrowser browser;

    @BeforeEach
    void createFixture() throws Exception {
        Path file = tempDir.resolve("sales.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE sales (city TEXT, amount REAL, qty INTEGER, value INTEGER)");
            st.execute("INSERT INTO sales VALUES ('Oslo', 10, 1, 7), ('Oslo', 30, 2, 7), ('Lima', 5, 3, 8),"
                    + " ('Lima', 5, 4, 8), ('Lima', 20, 5, 9), (NULL, 1, 6, 9)");
        }
        browser = DatabaseBrowser.sqlite(file);
    }

    private List<List<String>> chartRows(String query, Spec spec) throws Exception {
        return browser.query(ChartMaker.sql(query, spec), ChartMaker.limit(spec.kind())).rows();
    }

    @Test
    void barCountsRowsPerGroupBiggestFirst() throws Exception {
        List<List<String>> rows = chartRows("SELECT * FROM sales", new Spec(Kind.BAR, "city", Measure.COUNT, null));

        assertEquals(List.of(List.of("Lima", "3"), List.of("Oslo", "2"), Arrays.asList("NULL", "1")), rows);
    }

    @Test
    void measuresAggregateTheValueColumn() throws Exception {
        assertEquals(List.of(List.of("Oslo", "40.0"), List.of("Lima", "30.0"), List.of("NULL", "1.0")),
                chartRows("SELECT * FROM sales", new Spec(Kind.BAR, "city", Measure.SUM, "amount")));
        assertEquals(List.of("Oslo", "20.0"),
                chartRows("SELECT * FROM sales", new Spec(Kind.PIE, "city", Measure.AVERAGE, "amount")).get(0));
        assertEquals(List.of("Oslo", "30.0"),
                chartRows("SELECT * FROM sales", new Spec(Kind.BAR, "city", Measure.MAX, "amount")).get(0));
    }

    @Test
    void aColumnCalledValueIsNotMistakenForTheAggregate() throws Exception {
        assertEquals(List.of(List.of("7", "2"), List.of("8", "2"), List.of("9", "2")),
                chartRows("SELECT * FROM sales", new Spec(Kind.LINE, "value", Measure.COUNT, null)));
    }

    @Test
    void lineRunsInXOrder() throws Exception {
        List<List<String>> rows = chartRows("SELECT qty, amount FROM sales",
                new Spec(Kind.LINE, "qty", Measure.SUM, "amount"));

        assertEquals(List.of("1", "2", "3", "4", "5", "6"), rows.stream().map(r -> r.get(0)).toList());
    }

    @Test
    void scatterPlotsRowsWithBothValues() throws Exception {
        List<List<String>> rows = chartRows("SELECT qty, amount FROM sales WHERE city IS NOT NULL",
                new Spec(Kind.SCATTER, "qty", null, "amount"));

        assertEquals(5, rows.size());
        assertEquals(List.of("1", "10.0"), rows.get(0));
    }

    @Test
    void chartsWorkOnTheUsersOwnGroupedQueryAndItsTrailingSemicolon() throws Exception {
        List<List<String>> rows = chartRows(
                "-- totals\nSELECT city, SUM(amount) AS total FROM sales GROUP BY city ORDER BY total;",
                new Spec(Kind.BAR, "city", Measure.SUM, "total"));

        assertEquals(List.of("Oslo", "40.0"), rows.get(0));
    }

    @Test
    void numericColumnsIgnoreNullsAndNeedAtLeastOneNumber() {
        TablePreview result = new TablePreview(List.of("city", "amount", "code", "blank"), List.of(
                List.of("Oslo", "10.5", "007", "NULL"),
                List.of("Lima", "NULL", "A1", "NULL"),
                List.of("Rome", "-2e3", "12", "NULL")), 3);

        assertEquals(List.of("amount"), ChartMaker.numericColumns(result));
    }

    @Test
    void problemsAreExplainedBeforeAnyQueryRuns() {
        List<String> columns = List.of("city", "amount");
        List<String> numeric = List.of("amount");

        assertNull(ChartMaker.problem(new Spec(Kind.BAR, "city", Measure.COUNT, null), columns, numeric));
        assertNull(ChartMaker.problem(new Spec(Kind.BAR, "city", Measure.SUM, "amount"), columns, numeric));
        assertEquals("Pick a column to group by.",
                ChartMaker.problem(new Spec(Kind.BAR, null, Measure.COUNT, null), columns, numeric));
        assertEquals("Pick a number column to measure.",
                ChartMaker.problem(new Spec(Kind.BAR, "city", Measure.SUM, null), columns, numeric));
        assertEquals("city isn't a number column.",
                ChartMaker.problem(new Spec(Kind.BAR, "amount", Measure.SUM, "city"), columns, numeric));
        assertEquals("A scatter plot needs numbers on both axes; city isn't a number column.",
                ChartMaker.problem(new Spec(Kind.SCATTER, "city", null, "amount"), columns, numeric));
        assertEquals("This result has no number columns to measure. Try Count of rows.",
                ChartMaker.problem(new Spec(Kind.PIE, "city", Measure.AVERAGE, null), List.of("city"), List.of()));
    }

    @Test
    void descriptionSaysWhatIsShownAndWhatWasLeftOut() {
        Spec count = new Spec(Kind.BAR, "city", Measure.COUNT, null);
        Spec average = new Spec(Kind.BAR, "city", Measure.AVERAGE, "amount");

        assertEquals("Count of rows by city: 3 groups.", ChartMaker.describe(count, 3, 3));
        assertEquals("Average of amount by city: showing the 40 biggest of 212 groups.",
                ChartMaker.describe(average, 40, 212));
        assertEquals("Count of rows by qty: showing the first 2,000 of 5,000 groups.",
                ChartMaker.describe(new Spec(Kind.LINE, "qty", Measure.COUNT, null), 2000, 5000));
        assertEquals("Count of rows by city: 14 groups. The smallest 5 are added up as Other.",
                ChartMaker.describe(new Spec(Kind.PIE, "city", Measure.COUNT, null), 14, 14));
        assertEquals("amount against qty: 1 point.",
                ChartMaker.describe(new Spec(Kind.SCATTER, "qty", null, "amount"), 1, 1));
        assertEquals("Count of rows by city: the query returned no rows.", ChartMaker.describe(count, 0, 0));
    }

    @Test
    void repeatedAndEmptyLabelsStayDistinct() {
        Set<String> used = new HashSet<>();

        assertEquals("1", ChartMaker.uniqueLabel("1", used));
        assertEquals("1 (2)", ChartMaker.uniqueLabel("1", used));
        assertEquals("(empty)", ChartMaker.uniqueLabel("NULL", used));
        assertEquals("(empty) (2)", ChartMaker.uniqueLabel(null, used));
    }

    @Test
    void onlyPlainDecimalsCountAsNumbers() {
        assertEquals(1.5, ChartMaker.parse(" 1.5 "));
        assertEquals(-2000.0, ChartMaker.parse("-2e3"));
        assertNull(ChartMaker.parse("NaN"));
        assertNull(ChartMaker.parse("Infinity"));
        assertNull(ChartMaker.parse("5f"));
        assertNull(ChartMaker.parse("0x10"));
        assertNull(ChartMaker.parse(""));
        assertNull(ChartMaker.parse(null));
    }
}

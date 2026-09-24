package com.example.helloapplication;

import com.example.helloapplication.ChartMaker.Kind;
import com.example.helloapplication.ChartMaker.Measure;
import com.example.helloapplication.ChartMaker.Spec;
import com.example.helloapplication.DatabaseBrowser.ForeignKey;
import com.example.helloapplication.DatabaseBrowser.TablePreview;
import com.example.helloapplication.DatabaseBrowser.TableRef;
import com.example.helloapplication.QueryBuilder.Filter;
import com.example.helloapplication.QueryBuilder.Op;
import com.example.helloapplication.QueryBuilder.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryBuilderTest {

    @TempDir
    Path tempDir;

    private DatabaseBrowser browser;
    private Map<String, List<String>> columns;

    @BeforeEach
    void openSample() throws Exception {
        browser = DatabaseBrowser.sqlite(SampleDatabase.ensure(tempDir.resolve("sample.db")));
        columns = new LinkedHashMap<>();
        for (TableRef table : browser.listTables()) {
            columns.put(table.name(), browser.listColumns(table));
        }
    }

    private ForeignKey link(String child, String parent) throws Exception {
        return browser.listForeignKeys().stream()
                .filter(k -> k.child().name().equals(child) && k.parent().name().equals(parent))
                .findFirst().orElseThrow();
    }

    private TablePreview run(Request request) throws Exception {
        return browser.query(QueryBuilder.sql(request, columns), 1000);
    }

    @Test
    void aPlainTableIsTheStarterQuery() {
        assertEquals(Home_View.starterQuery("orders"), QueryBuilder.sql(Request.of("orders"), columns));
        assertEquals(Home_View.starterQuery("order"), QueryBuilder.sql(Request.of("order"), Map.of()));
    }

    @Test
    void conditionsAreWrittenForEachOperator() {
        assertEquals("\"city\" = 'Boston'", QueryBuilder.condition(new Filter("city", Op.IS, "Boston")));
        assertEquals("(\"city\" IS NULL OR \"city\" <> 'Boston')",
                QueryBuilder.condition(new Filter("city", Op.IS_NOT, "Boston")));
        assertEquals("\"name\" LIKE '%pen%' ESCAPE '\\'", QueryBuilder.condition(new Filter("name", Op.CONTAINS, "pen")));
        assertEquals("\"unit_price\" > 5", QueryBuilder.condition(new Filter("unit_price", Op.GREATER, " 5 ")));
        assertEquals("\"unit_price\" < 2.5", QueryBuilder.condition(new Filter("unit_price", Op.LESS, "2.5")));
        assertEquals("(\"shipped_on\" IS NULL OR \"shipped_on\" = '')",
                QueryBuilder.condition(new Filter("shipped_on", Op.EMPTY, "")));
        assertEquals("(\"shipped_on\" IS NOT NULL AND \"shipped_on\" <> '')",
                QueryBuilder.condition(new Filter("shipped_on", Op.NOT_EMPTY, "")));
        assertEquals("\"customers.city\" = 'Oslo'", QueryBuilder.condition(new Filter("customers.city", Op.IS, "Oslo")));
    }

    @Test
    void valuesAreEscaped() {
        assertEquals("'O''Brien'", QueryBuilder.literal("O'Brien"));
        assertEquals("'02134'", QueryBuilder.literal("02134"), "a leading zero stays text, like a zip code");
        assertEquals("-3", QueryBuilder.literal("-3"));
        assertEquals("'1e5'", QueryBuilder.literal("1e5"));
        assertEquals("\"note\" LIKE '%50\\% off\\_now''s%' ESCAPE '\\'",
                QueryBuilder.condition(new Filter("note", Op.CONTAINS, "50% off_now's")));
    }

    @Test
    void awkwardValuesStillRunAsOneReadOnlySelect() throws Exception {
        for (String value : List.of("O'Brien; DROP TABLE customers", "--", "/* x */", "\"", "100%", "a_b\\c")) {
            for (Op op : List.of(Op.IS, Op.CONTAINS, Op.GREATER)) {
                assertDoesNotThrow(() -> run(new Request("customers", List.of(),
                        List.of(new Filter("last_name", op, value)), null, false)), value + " " + op);
            }
        }
        assertEquals(40, run(Request.of("customers")).totalRows(), "the table is untouched");
    }

    @Test
    void linkedColumnsCanBeFilteredAndSorted() throws Exception {
        Request request = new Request("orders", List.of(link("orders", "customers")),
                List.of(new Filter("customers.city", Op.IS, "Boston")), "placed_on", true);

        TablePreview result = run(request);

        assertEquals(QueryBuilder.columns(request, columns), result.columns());
        int city = result.columns().indexOf("customers.city");
        int placed = result.columns().indexOf("placed_on");
        assertTrue(city >= 0 && placed >= 0);
        assertFalse(result.columns().contains("customers.customer_id"), "the key it was joined on isn't repeated");
        assertFalse(result.rows().isEmpty());
        List<String> dates = new ArrayList<>();
        for (List<String> row : result.rows()) {
            assertEquals("Boston", row.get(city));
            dates.add(row.get(placed));
        }
        List<String> sorted = new ArrayList<>(dates);
        sorted.sort(java.util.Comparator.reverseOrder());
        assertEquals(sorted, dates);
    }

    @Test
    void numberFiltersCompareAsNumbers() throws Exception {
        TablePreview result = run(new Request("products", List.of(),
                List.of(new Filter("unit_price", Op.GREATER, "10")), "unit_price", false));

        assertEquals(List.of("12.5", "24.0"),
                result.rows().stream().map(r -> r.get(result.columns().indexOf("unit_price"))).toList());
    }

    @Test
    void twoLinksKeepEveryRowOfTheTable() throws Exception {
        Request request = new Request("order_items",
                List.of(link("order_items", "orders"), link("order_items", "products")), List.of(), null, false);

        TablePreview result = run(request);

        assertEquals(run(Request.of("order_items")).totalRows(), result.totalRows());
        assertTrue(result.columns().containsAll(List.of("orders.status", "products.name", "quantity")));
    }

    @Test
    void linksToTheSameTableGetDistinctNames() {
        TableRef people = new TableRef(null, "people");
        TableRef trips = new TableRef(null, "trips");
        ForeignKey driver = new ForeignKey(trips, "driver_id", people, "id");
        ForeignKey rider = new ForeignKey(trips, "rider_id", people, "id");
        ForeignKey manager = new ForeignKey(people, "manager_id", people, "id");

        assertEquals("people", QueryBuilder.linkName(driver, List.of(driver)));
        assertEquals("people (rider_id)", QueryBuilder.linkName(rider, List.of(driver, rider)));
        assertEquals("parent people", QueryBuilder.linkName(manager, List.of(manager)));
    }

    @Test
    void chartsGroupByALinkedColumn() throws Exception {
        String sql = QueryBuilder.sql(new Request("orders", List.of(link("orders", "customers")),
                List.of(), null, false), columns);

        List<List<String>> rows = browser.query(
                ChartMaker.sql(sql, new Spec(Kind.BAR, "customers.city", Measure.COUNT, null)), 100).rows();

        long total = rows.stream().mapToLong(r -> Long.parseLong(r.get(1))).sum();
        assertEquals(run(Request.of("orders")).totalRows(), total);
    }
}

package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;
import com.example.helloapplication.RelationshipFinder.Confidence;
import com.example.helloapplication.RelationshipFinder.Relationship;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipFinderTest {

    private static CsvTable table(String name, List<String> columns, String[]... rows) {
        List<List<String>> data = new ArrayList<>();
        for (String[] row : rows) {
            data.add(Arrays.stream(row).map(v -> v.isEmpty() ? null : v).toList());
        }
        return new CsvTable(name, columns, data);
    }

    private static String describe(Relationship r) {
        return r.childTable() + "." + r.childColumn() + " -> " + r.parentTable() + "." + r.parentColumn()
                + " " + r.confidence();
    }

    private static List<String> find(CsvTable... tables) {
        return RelationshipFinder.find(List.of(tables)).stream().map(RelationshipFinderTest::describe).toList();
    }

    private static final CsvTable CUSTOMERS = table("customers", List.of("customer_id", "name"),
            new String[]{"1", "Ada"}, new String[]{"2", "Grace"}, new String[]{"3", "Alan"});

    @Test
    void sameColumnNameAndAllValuesMatchIsStrong() {
        CsvTable orders = table("orders", List.of("order_id", "customer_id", "quantity"),
                new String[]{"10", "1", "1"}, new String[]{"11", "2", "3"}, new String[]{"12", "2", "2"});

        assertEquals(List.of("orders.customer_id -> customers.customer_id STRONG"), find(CUSTOMERS, orders));
    }

    @Test
    void columnNamedAfterTheTableMatchesItsIdColumn() {
        CsvTable categories = table("categories", List.of("id", "label"),
                new String[]{"1", "Paper"}, new String[]{"2", "Pens"});
        CsvTable products = table("products", List.of("id", "category_id"),
                new String[]{"1", "1"}, new String[]{"2", "2"}, new String[]{"3", "1"});

        assertEquals(List.of("products.category_id -> categories.id STRONG"), find(categories, products));
    }

    @Test
    void aFewUnmatchedValuesMakeItLikely() {
        String[][] rows = new String[20][];
        for (int i = 0; i < 20; i++) {
            rows[i] = new String[]{String.valueOf(100 + i), i == 0 ? "99" : String.valueOf(1 + i % 3)};
        }
        CsvTable orders = table("orders", List.of("order_id", "customer_id"), rows);

        // 3 of the 4 distinct values match (75%): below the 90% bar, so no link at all.
        assertEquals(List.of(), find(CUSTOMERS, orders));

        List<String> many = new ArrayList<>();
        String[][] customers = new String[30][];
        for (int i = 0; i < 30; i++) {
            customers[i] = new String[]{String.valueOf(i + 1)};
        }
        String[][] orderRows = new String[31][];
        for (int i = 0; i < 30; i++) {
            orderRows[i] = new String[]{String.valueOf(1000 + i), String.valueOf(i + 1)};
        }
        orderRows[30] = new String[]{"2000", "77"}; // 30 of 31 distinct values match
        many.addAll(find(table("customers", List.of("customer_id"), customers),
                table("orders", List.of("order_id", "customer_id"), orderRows)));
        assertEquals(List.of("orders.customer_id -> customers.customer_id LIKELY"), many);
    }

    @Test
    void coincidentalSmallIntegersAreNotLinked() {
        // quantity values 1..3 all exist as customer ids, but nothing in the names connects them.
        CsvTable lines = table("order_lines", List.of("line_id", "quantity"),
                new String[]{"1", "1"}, new String[]{"2", "2"}, new String[]{"3", "3"});

        assertEquals(List.of(), find(CUSTOMERS, lines));
    }

    @Test
    void valuesOnlyMatchOnTextKeysIsPossible() {
        String[][] products = new String[12][];
        String[][] sales = new String[12][];
        for (int i = 0; i < 12; i++) {
            products[i] = new String[]{"SKU-" + i, "item " + i};
            sales[i] = new String[]{String.valueOf(i), "SKU-" + i};
        }
        List<String> found = find(table("products", List.of("sku", "title"), products),
                table("sales", List.of("sale_id", "item_code"), sales));

        assertEquals(List.of("sales.item_code -> products.sku POSSIBLE"), found);
    }

    @Test
    void numbersAreComparedByValue() {
        CsvTable orders = table("orders", List.of("order_id", "customer_id"),
                new String[]{"10", "001"}, new String[]{"11", "2.0"});

        assertEquals(List.of("orders.customer_id -> customers.customer_id STRONG"), find(CUSTOMERS, orders));
        assertEquals("7", RelationshipFinder.normalize(" 007 "));
        assertEquals("7.5", RelationshipFinder.normalize("7.50"));
        assertEquals("SKU-7", RelationshipFinder.normalize("SKU-7"));
    }

    @Test
    void selfReferenceThroughManagerId() {
        CsvTable employees = table("employees", List.of("employee_id", "name", "manager_id"),
                new String[]{"1", "Ada", ""}, new String[]{"2", "Grace", "1"}, new String[]{"3", "Alan", "1"});

        assertEquals(List.of("employees.manager_id -> employees.employee_id STRONG"), find(employees));
    }

    @Test
    void oneToOneTablesLinkOneWayOnly() {
        CsvTable users = table("users", List.of("user_id", "email"),
                new String[]{"1", "a@x"}, new String[]{"2", "b@x"});
        CsvTable profiles = table("profiles", List.of("user_id", "bio"),
                new String[]{"1", "hi"}, new String[]{"2", "yo"});

        assertEquals(List.of("profiles.user_id -> users.user_id STRONG"), find(users, profiles));
    }

    @Test
    void genericNamesAloneAreNotEvidence() {
        assertFalse(RelationshipFinder.namesAgree("id", "customers", "id"));
        assertFalse(RelationshipFinder.namesAgree("name", "customers", "name"));
        assertTrue(RelationshipFinder.namesAgree("customer_id", "customers", "id"));
        assertTrue(RelationshipFinder.namesAgree("Customer ID", "Customers", "customer_id"));
        assertTrue(RelationshipFinder.namesAgree("category_id", "categories", "id"));
        assertTrue(RelationshipFinder.namesAgree("sku", "products", "sku"));
    }

    @Test
    void singularHandlesCommonEndings() {
        assertEquals("category", RelationshipFinder.singular("categories"));
        assertEquals("address", RelationshipFinder.singular("addresses"));
        assertEquals("order_item", RelationshipFinder.singular("order_items"));
        assertEquals("status", RelationshipFinder.singular("status"));
    }

    @Test
    void matchRateIsMatchedOverDistinct() {
        Relationship r = new Relationship("a", "b", "c", "d", 9, 10, Confidence.LIKELY);
        assertEquals(0.9, r.matchRate(), 1e-9);
    }
}

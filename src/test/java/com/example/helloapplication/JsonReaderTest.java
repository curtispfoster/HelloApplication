package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void arrayOfObjectsIsOneTableNamedAfterTheFile() throws Exception {
        List<CsvTable> tables = JsonReader.read(write("Team Members.json", """
                [{"id": 1, "name": "Ada", "active": true},
                 {"id": 2, "name": "Grace", "email": "grace@example.com"}]"""));

        assertEquals(1, tables.size());
        CsvTable table = tables.get(0);
        assertEquals("team_members", table.name());
        assertEquals(List.of("id", "name", "active", "email"), table.columns());
        assertEquals(Arrays.asList("1", "Ada", "true", null), table.rows().get(0));
        assertEquals(Arrays.asList("2", "Grace", null, "grace@example.com"), table.rows().get(1));
    }

    @Test
    void objectOfArraysIsOneTablePerArray() throws Exception {
        List<CsvTable> tables = JsonReader.read(write("shop.json", """
                {"Customers": [{"customer_id": 1}], "order lines": [{"order_id": 5, "customer_id": 1}],
                 "version": 3}"""));

        assertEquals(List.of("customers", "order_lines"), tables.stream().map(CsvTable::name).toList());
        assertEquals(List.of("order_id", "customer_id"), tables.get(1).columns());
    }

    @Test
    void aSingleWrappedArrayKeepsTheFileName() throws Exception {
        List<CsvTable> tables = JsonReader.read(write("sales.json", """
                {"meta": {"source": "export"}, "data": [{"amount": 1.50}, {"amount": -2e3}]}"""));

        assertEquals(1, tables.size());
        assertEquals("sales", tables.get(0).name());
        // Numbers are kept exactly as written, so type inference sees what the file says.
        assertEquals(List.of(List.of("1.50"), List.of("-2e3")), tables.get(0).rows());
    }

    @Test
    void nestedObjectsBecomeColumnsAndArraysStayJson() throws Exception {
        CsvTable table = JsonReader.read(write("people.json", """
                [{"name": "Ada", "address": {"city": "London", "geo": {"lat": 51.5}}, "langs": ["en", "fr"],
                  "note": "say \\"hi\\"\\n", "empty": {}, "blank": ""}]""")).get(0);

        assertEquals(List.of("name", "address_city", "address_geo_lat", "langs", "note", "empty", "blank"),
                table.columns());
        assertEquals(Arrays.asList("Ada", "London", "51.5", "[\"en\",\"fr\"]", "say \"hi\"\n", "{}", null),
                table.rows().get(0));
    }

    @Test
    void jsonLinesAreOneRowPerLine() throws Exception {
        CsvTable table = JsonReader.read(write("events.jsonl", """
                {"event": "login", "user": 1}

                {"event": "logout", "user": 1, "seconds": 30}
                """)).get(0);

        assertEquals("events", table.name());
        assertEquals(List.of("event", "user", "seconds"), table.columns());
        assertEquals(2, table.rows().size());
    }

    @Test
    void scalarsAndPlainObjectsStillMakeATable() {
        CsvTable numbers = JsonReader.tables("n", new JsonReader.Parser("[1, 2, null]").parseDocument()).get(0);
        assertEquals(List.of("value"), numbers.columns());
        assertEquals(Arrays.asList(List.of("1"), List.of("2"), Arrays.asList((String) null)), numbers.rows());

        CsvTable settings = JsonReader.tables("s", new JsonReader.Parser("{\"a\": 1, \"b\": \"x\"}").parseDocument()).get(0);
        assertEquals(List.of("a", "b"), settings.columns());
        assertEquals(1, settings.rows().size());

        CsvTable empty = JsonReader.tables("e", new JsonReader.Parser("[]").parseDocument()).get(0);
        assertEquals(List.of("value"), empty.columns());
        assertTrue(empty.rows().isEmpty());
    }

    @Test
    void keysThatDifferOnlyByCaseGetUniqueColumns() {
        CsvTable table = JsonReader.tables("t",
                new JsonReader.Parser("[{\"Name\": \"a\", \"name\": \"b\"}]").parseDocument()).get(0);

        assertEquals(List.of("Name", "name_2"), table.columns());
        assertEquals(List.of("a", "b"), table.rows().get(0));
    }

    @Test
    void unicodeEscapesAndBomAreHandled() throws Exception {
        Path file = tempDir.resolve("u.json");
        byte[] body = "[{\"city\": \"Z\\u00fcrich\"}]".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        Files.write(file, withBom);

        assertEquals("Zürich", JsonReader.read(file).get(0).rows().get(0).get(0));
    }

    @Test
    void invalidJsonSaysWhereItWentWrong() throws Exception {
        IOException e = assertThrows(IOException.class,
                () -> JsonReader.read(write("bad.json", "[{\"a\": 1},\n {\"a\": 2,}]")));
        assertEquals("bad.json isn't valid JSON: expected a quoted key (line 2, column 10)", e.getMessage());

        assertThrows(IOException.class, () -> JsonReader.read(write("trailing.json", "[1] [2]")));
        assertThrows(IOException.class, () -> JsonReader.read(write("open.json", "{\"a\": \"never closed")));
        assertThrows(IOException.class, () -> JsonReader.read(write("num.json", "[01.]")));
        assertEquals("empty.json is empty.",
                assertThrows(IOException.class, () -> JsonReader.read(write("empty.json", "  \n"))).getMessage());
    }

    @Test
    void deepNestingIsRefusedNotAStackOverflow() throws Exception {
        String deep = "[".repeat(10_000) + "]".repeat(10_000);

        IOException e = assertThrows(IOException.class, () -> JsonReader.read(write("deep.json", deep)));
        assertTrue(e.getMessage().contains("nested more than"), e.getMessage());
    }
}

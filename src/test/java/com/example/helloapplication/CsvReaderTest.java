package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void readsQuotedFieldsWithCommasNewlinesAndQuotes() throws Exception {
        CsvTable table = CsvReader.read(write("notes.csv",
                "id,note\r\n1,\"Hello, world\"\r\n2,\"line one\nline two\"\r\n3,\"She said \"\"hi\"\"\"\r\n"));

        assertEquals(List.of("id", "note"), table.columns());
        assertEquals(List.of(
                List.of("1", "Hello, world"),
                List.of("2", "line one\nline two"),
                List.of("3", "She said \"hi\"")), table.rows());
    }

    @Test
    void emptyFieldsBecomeNullAndShortRowsArePadded() throws Exception {
        CsvTable table = CsvReader.read(write("t.csv", "a,b,c\n1,,3\n4\n"));

        assertEquals(Arrays.asList("1", null, "3"), table.rows().get(0));
        assertEquals(Arrays.asList("4", null, null), table.rows().get(1));
    }

    @Test
    void extraFieldsAreDroppedAndBlankLinesSkipped() throws Exception {
        CsvTable table = CsvReader.read(write("t.csv", "a,b\n\n1,2,3\n\n"));

        assertEquals(List.of(List.of("1", "2")), table.rows());
    }

    @Test
    void detectsSemicolonAndTabDelimiters() {
        assertEquals(';', CsvReader.detectDelimiter("id;name;price\n1;a;2,5"));
        assertEquals('\t', CsvReader.detectDelimiter("id\tname\n1\tx"));
        assertEquals(',', CsvReader.detectDelimiter("\"a;b;c\",d\n"));
        assertEquals(',', CsvReader.detectDelimiter("single"));
    }

    @Test
    void stripsUtf8BomAndFallsBackToWindows1252() throws Exception {
        Path bom = tempDir.resolve("bom.csv");
        Files.write(bom, ("﻿id,city\n1,Zürich\n").getBytes(StandardCharsets.UTF_8));
        assertEquals("id", CsvReader.read(bom).columns().get(0));
        assertEquals("Zürich", CsvReader.read(bom).rows().get(0).get(1));

        Path ansi = tempDir.resolve("ansi.csv");
        Files.write(ansi, "id,city\n1,Zürich\n".getBytes(Charset.forName("windows-1252")));
        assertEquals("Zürich", CsvReader.read(ansi).rows().get(0).get(1));
    }

    @Test
    void headerNamesAreMadeUnique() {
        assertEquals(List.of("id", "Name", "name_2", "column_4"),
                CsvReader.uniqueColumnNames(Arrays.asList("id", " Name ", "name", "")));
    }

    @Test
    void tableNameComesFromTheFileName() {
        assertEquals("order_items_2024", CsvReader.tableName(Path.of("Order Items (2024).csv")));
        assertEquals("t_2024_sales", CsvReader.tableName(Path.of("2024 sales.csv")));
        assertEquals("table", CsvReader.tableName(Path.of("---.csv")));
    }

    @Test
    void emptyFileIsRefused() throws Exception {
        assertThrows(IOException.class, () -> CsvReader.read(write("empty.csv", "")));
    }
}

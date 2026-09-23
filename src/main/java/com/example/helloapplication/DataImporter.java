package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;
import com.example.helloapplication.RelationshipFinder.Confidence;
import com.example.helloapplication.RelationshipFinder.Relationship;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class DataImporter {

    public static final Path DEFAULT_DIRECTORY = Path.of("data", "imports");

    private static final int BATCH_SIZE = 5_000;
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d{1,18}");

    public record ImportResult(Path database, List<String> tables,
                               List<Relationship> saved, List<Relationship> notSaved) {}

    private DataImporter() {
    }

    public static boolean canImport(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".csv") || name.endsWith(".tsv") || isJson(name);
    }

    private static boolean isJson(String lowerCaseName) {
        return lowerCaseName.endsWith(".json") || lowerCaseName.endsWith(".jsonl")
                || lowerCaseName.endsWith(".ndjson");
    }

    public static ImportResult importFiles(List<Path> files) throws IOException, SQLException {
        return importFiles(files, DEFAULT_DIRECTORY);
    }

    public static ImportResult importFiles(List<Path> files, Path directory) throws IOException, SQLException {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No files to import.");
        }
        List<CsvTable> tables = withUniqueNames(readAll(files));
        List<Relationship> found = RelationshipFinder.find(tables);
        List<Relationship> saved = new ArrayList<>();
        List<Relationship> notSaved = new ArrayList<>();
        for (Relationship r : found) {
            (r.confidence() == Confidence.POSSIBLE ? notSaved : saved).add(r);
        }

        Files.createDirectories(directory);
        Path database = uniqueFile(directory, datasetName(tables), ".db");
        Path partial = database.resolveSibling(database.getFileName() + ".partial");
        Files.deleteIfExists(partial);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + partial.toAbsolutePath())) {
            // The .partial file is thrown away on any failure, so skip the journal and fsyncs while filling it.
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode = OFF");
                st.execute("PRAGMA synchronous = OFF");
            }
            conn.setAutoCommit(false);
            for (CsvTable table : tables) {
                writeTable(conn, table, saved);
            }
            conn.commit();
        } catch (SQLException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        Files.move(partial, database, StandardCopyOption.ATOMIC_MOVE);

        return new ImportResult(database, tables.stream().map(CsvTable::name).toList(), saved, notSaved);
    }

    private static List<CsvTable> readAll(List<Path> files) throws IOException {
        List<CsvTable> tables = new ArrayList<>();
        for (Path file : files) {
            if (isJson(file.getFileName().toString().toLowerCase(Locale.ROOT))) {
                tables.addAll(JsonReader.read(file));
            } else {
                tables.add(CsvReader.read(file));
            }
        }
        return tables;
    }

    static String datasetName(List<CsvTable> tables) {
        List<String> names = tables.stream().map(t -> t.name().toLowerCase(Locale.ROOT)).toList();
        if (names.size() <= 3) {
            return String.join("-", names);
        }
        return names.get(0) + "-" + names.get(1) + "-and-" + (names.size() - 2) + "-more";
    }

    static List<CsvTable> withUniqueNames(List<CsvTable> tables) {
        Set<String> used = new HashSet<>();
        List<CsvTable> renamed = new ArrayList<>();
        for (CsvTable table : tables) {
            String name = table.name();
            for (int n = 2; !used.add(name.toLowerCase(Locale.ROOT)); n++) {
                name = table.name() + "_" + n;
            }
            renamed.add(name.equals(table.name()) ? table : new CsvTable(name, table.columns(), table.rows()));
        }
        return renamed;
    }

    static Path uniqueFile(Path directory, String stem, String extension) {
        Path file = directory.resolve(stem + extension);
        for (int n = 2; Files.exists(file); n++) {
            file = directory.resolve(stem + "-" + n + extension);
        }
        return file;
    }

    private static void writeTable(Connection conn, CsvTable table, List<Relationship> saved) throws SQLException {
        List<String> types = new ArrayList<>();
        for (int c = 0; c < table.columns().size(); c++) {
            types.add(inferType(table, c));
        }

        // Every column another table points at must be a key (PRIMARY KEY or UNIQUE) for the foreign key to be valid.
        Set<String> referenced = new LinkedHashSet<>();
        List<Relationship> outgoing = new ArrayList<>();
        for (Relationship r : saved) {
            if (r.parentTable().equals(table.name())) {
                referenced.add(r.parentColumn());
            }
            if (r.childTable().equals(table.name())) {
                outgoing.add(r);
            }
        }
        String primaryKey = choosePrimaryKey(table, referenced);

        String q = "\"";
        List<String> parts = new ArrayList<>();
        for (int c = 0; c < table.columns().size(); c++) {
            parts.add(DatabaseBrowser.quoteIdentifier(table.columns().get(c), q) + " " + types.get(c));
        }
        if (primaryKey != null) {
            parts.add("PRIMARY KEY (" + DatabaseBrowser.quoteIdentifier(primaryKey, q) + ")");
        }
        for (String column : referenced) {
            if (!column.equals(primaryKey)) {
                parts.add("UNIQUE (" + DatabaseBrowser.quoteIdentifier(column, q) + ")");
            }
        }
        for (Relationship r : outgoing) {
            parts.add("FOREIGN KEY (" + DatabaseBrowser.quoteIdentifier(r.childColumn(), q) + ") REFERENCES "
                    + DatabaseBrowser.quoteIdentifier(r.parentTable(), q)
                    + " (" + DatabaseBrowser.quoteIdentifier(r.parentColumn(), q) + ")");
        }
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE " + DatabaseBrowser.quoteIdentifier(table.name(), q)
                    + " (\n    " + String.join(",\n    ", parts) + "\n)");
        }

        String placeholders = String.join(", ", Collections.nCopies(table.columns().size(), "?"));
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO " + DatabaseBrowser.quoteIdentifier(table.name(), q) + " VALUES (" + placeholders + ")")) {
            int pending = 0;
            for (List<String> row : table.rows()) {
                for (int c = 0; c < row.size(); c++) {
                    insert.setObject(c + 1, typedValue(row.get(c), types.get(c)));
                }
                insert.addBatch();
                if (++pending == BATCH_SIZE) { // bounded, so a big file doesn't hold a second copy of every row
                    insert.executeBatch();
                    pending = 0;
                }
            }
            insert.executeBatch();
        }
    }

    private static String choosePrimaryKey(CsvTable table, Set<String> referenced) {
        for (String column : referenced) {
            if (RelationshipFinder.isOwnId(table.name(), column)) {
                return column;
            }
        }
        if (!referenced.isEmpty()) {
            return referenced.iterator().next();
        }
        for (String key : RelationshipFinder.keyColumns(table).keySet()) {
            if (RelationshipFinder.isOwnId(table.name(), key)) {
                return key;
            }
        }
        return null;
    }

    static String inferType(CsvTable table, int column) {
        boolean integer = true;
        boolean real = true;
        boolean any = false;
        for (List<String> row : table.rows()) {
            String v = row.get(column);
            if (v == null) {
                continue;
            }
            any = true;
            String t = v.trim();
            if (!t.equals(v) || (t.length() > 1 && t.startsWith("0") && !t.startsWith("0."))) {
                return "TEXT";
            }
            if (integer && !INTEGER.matcher(t).matches()) {
                integer = false;
            }
            if (real && !ChartMaker.DECIMAL.matcher(t).matches()) {
                real = false;
            }
            if (!integer && !real) {
                return "TEXT";
            }
        }
        if (!any) {
            return "TEXT";
        }
        return integer ? "INTEGER" : "REAL";
    }

    private static Object typedValue(String value, String type) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case "INTEGER" -> Long.parseLong(value);
            case "REAL" -> Double.parseDouble(value);
            default -> value;
        };
    }
}

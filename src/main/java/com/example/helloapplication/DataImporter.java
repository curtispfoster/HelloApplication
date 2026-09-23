package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;
import com.example.helloapplication.RelationshipFinder.Analysis;
import com.example.helloapplication.RelationshipFinder.Confidence;
import com.example.helloapplication.RelationshipFinder.Keys;
import com.example.helloapplication.RelationshipFinder.Relationship;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public final class DataImporter {

    public static final Path DEFAULT_DIRECTORY = Path.of("data", "imports");

    private static final long PROGRESS_EVERY_MS = 500;
    private static final int BATCH_ROWS = 5_000;

    public record ImportResult(Path database, List<String> tables,
                               List<Relationship> saved, List<Relationship> notSaved) {}

    /** Hears what an import is doing, from the thread running it. */
    @FunctionalInterface
    public interface Progress {
        Progress NONE = message -> {};

        void update(String message);
    }

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
        return importFiles(files, DEFAULT_DIRECTORY, Progress.NONE);
    }

    public static ImportResult importFiles(List<Path> files, Path directory) throws IOException, SQLException {
        return importFiles(files, directory, Progress.NONE);
    }

    /**
     * Imports the files into a new database in {@code directory}. Rows are streamed into a scratch database as
     * they're read, so a CSV file of any size fits; the scratch file and the half-built database are deleted on
     * failure, and when the thread is interrupted the import stops at the next row batch.
     */
    public static ImportResult importFiles(List<Path> files, Path directory, Progress progress)
            throws IOException, SQLException {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No files to import.");
        }
        Files.createDirectories(directory);
        // The name comes from the tables, which aren't known until the files are read.
        Path partial = Files.createTempFile(directory, "import-", ".db.partial");
        Path scratch = Files.createTempFile(directory, "import-", ".stage");
        List<String> names;
        List<Relationship> saved = new ArrayList<>();
        List<Relationship> notSaved = new ArrayList<>();
        try {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + partial.toAbsolutePath())) {
                // The .partial file is thrown away on any failure, so skip the journal and fsyncs while filling it.
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA journal_mode = OFF");
                    st.execute("PRAGMA synchronous = OFF");
                    st.execute("PRAGMA cache_size = -131072"); // 128 MB
                }
                StagedTable.attach(conn, scratch);
                conn.setAutoCommit(false);

                List<StagedTable> tables = new ArrayList<>();
                for (Path file : files) {
                    stage(conn, file, tables, progress);
                }
                names = uniqueNames(tables.stream().map(t -> t.name).toList());
                for (int i = 0; i < tables.size(); i++) {
                    tables.get(i).name = names.get(i);
                }

                progress.update(tables.size() == 1 ? "Checking " + names.get(0) + "'s columns…"
                        : "Looking for links between the tables…");
                Analysis analysis = RelationshipFinder.find(conn, tables);
                for (Relationship r : analysis.relationships()) {
                    (r.confidence() == Confidence.POSSIBLE ? notSaved : saved).add(r);
                }
                for (StagedTable table : tables) {
                    checkCancelled();
                    progress.update("Saving " + table.name + " (" + ViewText.plural(table.rows(), "row") + ")…");
                    writeTable(conn, table, saved, analysis.keys());
                }
                conn.commit();
                conn.setAutoCommit(true);
                try (Statement st = conn.createStatement()) {
                    st.execute("DETACH DATABASE " + StagedTable.SCHEMA);
                }
            }
        } catch (IOException | SQLException | RuntimeException e) {
            Files.deleteIfExists(partial);
            throw e;
        } finally {
            Files.deleteIfExists(scratch);
        }

        Path database = uniqueFile(directory, datasetName(names), ".db");
        Files.move(partial, database, StandardCopyOption.ATOMIC_MOVE);
        return new ImportResult(database, names, saved, notSaved);
    }

    private static void stage(Connection conn, Path file, List<StagedTable> tables, Progress progress)
            throws IOException, SQLException {
        String fileName = file.getFileName().toString();
        if (isJson(fileName.toLowerCase(Locale.ROOT))) {
            progress.update("Reading " + fileName + "…");
            for (CsvTable table : JsonReader.read(file)) {
                tables.add(StagedTable.of(conn, tables.size() + 1, table));
            }
            return;
        }

        progress.update("Opening " + fileName + "…");
        try (CsvReader.Rows rows = CsvReader.open(file)) {
            StagedTable table = StagedTable.create(conn, tables.size() + 1, CsvReader.tableName(file), rows.columns());
            // Parsing and SQLite each take about half the time on a big file, so a second thread reads and
            // observes rows while this one writes them. The queue is short, so memory stays small.
            BlockingQueue<List<List<String>>> batches = new ArrayBlockingQueue<>(4);
            AtomicReference<Exception> readFailure = new AtomicReference<>();
            Thread reader = new Thread(() -> readBatches(rows, table, batches, readFailure), "csv-reader");
            reader.setDaemon(true);
            reader.start();
            try {
                long lastReport = System.currentTimeMillis();
                for (List<List<String>> batch = batches.take(); !batch.isEmpty(); batch = batches.take()) {
                    for (List<String> row : batch) {
                        table.write(row);
                    }
                    checkCancelled();
                    long now = System.currentTimeMillis();
                    if (now - lastReport >= PROGRESS_EVERY_MS) {
                        lastReport = now;
                        progress.update(readingMessage(fileName, rows.bytesRead(), rows.totalBytes(), table.rows()));
                    }
                }
            } catch (InterruptedException e) {
                throw new InterruptedIOException("The import was cancelled.");
            } finally {
                reader.interrupt();
                joinQuietly(reader);
            }
            if (readFailure.get() instanceof IOException e) {
                throw e;
            } else if (readFailure.get() instanceof RuntimeException e) {
                throw e;
            }
            table.finish();
            tables.add(table);
        }
    }

    /** Runs on the reader thread: parses rows into batches until the file ends, then sends an empty batch. */
    private static void readBatches(CsvReader.Rows rows, StagedTable table, BlockingQueue<List<List<String>>> batches,
                                    AtomicReference<Exception> failure) {
        try {
            List<List<String>> batch = new ArrayList<>(BATCH_ROWS);
            for (List<String> row = rows.next(); row != null; row = rows.next()) {
                table.observe(row);
                batch.add(row);
                if (batch.size() == BATCH_ROWS) {
                    batches.put(batch);
                    batch = new ArrayList<>(BATCH_ROWS);
                }
            }
            if (!batch.isEmpty()) {
                batches.put(batch);
            }
        } catch (InterruptedException e) {
            return; // the import thread stopped first and isn't waiting for more
        } catch (IOException | RuntimeException e) {
            failure.set(e);
        }
        try {
            batches.put(List.of());
        } catch (InterruptedException ignored) {
            // the import thread stopped first
        }
    }

    private static void joinQuietly(Thread thread) {
        boolean interrupted = false;
        while (true) {
            try {
                thread.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static String readingMessage(String fileName, long read, long total, long rows) {
        int percent = total == 0 ? 100 : (int) Math.min(100, read * 100 / total);
        return "Reading " + fileName + " (" + ViewText.fileSize(total) + "): " + percent + "%, "
                + ViewText.plural(rows, "row") + " so far…";
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("The import was cancelled.");
        }
    }

    static String datasetName(List<String> tables) {
        List<String> names = tables.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        if (names.size() <= 3) {
            return String.join("-", names);
        }
        return names.get(0) + "-" + names.get(1) + "-and-" + (names.size() - 2) + "-more";
    }

    static List<String> uniqueNames(List<String> tables) {
        Set<String> used = new HashSet<>();
        List<String> renamed = new ArrayList<>();
        for (String table : tables) {
            String name = table;
            for (int n = 2; !used.add(name.toLowerCase(Locale.ROOT)); n++) {
                name = table + "_" + n;
            }
            renamed.add(name);
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

    private static void writeTable(Connection conn, StagedTable table, List<Relationship> saved, Keys keys)
            throws SQLException {
        // Every column another table points at must be a key (PRIMARY KEY or UNIQUE) for the foreign key to be valid.
        Set<String> referenced = new LinkedHashSet<>();
        List<Relationship> outgoing = new ArrayList<>();
        for (Relationship r : saved) {
            if (r.parentTable().equals(table.name)) {
                referenced.add(r.parentColumn());
            }
            if (r.childTable().equals(table.name)) {
                outgoing.add(r);
            }
        }
        String primaryKey = choosePrimaryKey(table, referenced, keys);

        String q = "\"";
        List<String> parts = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (int c = 0; c < table.columns.size(); c++) {
            parts.add(DatabaseBrowser.quoteIdentifier(table.columns.get(c), q) + " " + table.type(c));
            values.add(table.typedExpression(c));
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
        String name = "main." + DatabaseBrowser.quoteIdentifier(table.name, q);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE " + name + " (\n    " + String.join(",\n    ", parts) + "\n)");
            st.execute("INSERT INTO " + name + " SELECT " + String.join(", ", values) + " FROM " + table.table());
        }
    }

    private static String choosePrimaryKey(StagedTable table, Set<String> referenced, Keys keys) throws SQLException {
        for (String column : referenced) {
            if (RelationshipFinder.isOwnId(table.name, column)) {
                return column;
            }
        }
        if (!referenced.isEmpty()) {
            return referenced.iterator().next();
        }
        for (int c = 0; c < table.columns.size(); c++) {
            if (RelationshipFinder.isOwnId(table.name, table.columns.get(c)) && keys.isKey(table, c)) {
                return table.columns.get(c);
            }
        }
        return null;
    }
}

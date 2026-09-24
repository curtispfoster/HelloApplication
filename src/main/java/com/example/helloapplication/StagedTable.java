package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;
import org.sqlite.Function;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One imported table while an import is running: its rows are written as plain text into a scratch database
 * (attached as {@code stage}) as they're read, so no file has to fit in memory. Along the way it works out each
 * column's type and keeps a small sample of values for {@link RelationshipFinder}. DataImporter then copies the
 * rows into the real, typed table.
 */
final class StagedTable {

    static final String SCHEMA = "stage";
    /** Rows at the start of a table that are sampled for repeats and values. */
    static final int SAMPLE_ROWS = 20_000;
    /** Distinct values kept per column from those rows. */
    static final int SAMPLE_VALUES = 1_000;

    private static final int BATCH_SIZE = 5_000;
    // What Java's String.trim() removes in practice, written so SQLite's trim() can remove exactly the same.
    private static final String TRIM_CHARS = " \t\n\r\f\u000B";
    private static final String SQL_TRIM_CHARS = "char(32, 9, 10, 13, 12, 11)";

    String name;
    final List<String> columns;
    private final String id;
    private final Column[] stats;
    private long rows;
    private long observed;
    private PreparedStatement insert;
    private int pending;

    private StagedTable(int number, String name, List<String> columns) {
        this.id = "s" + number;
        this.name = name;
        this.columns = List.copyOf(columns);
        this.stats = new Column[columns.size()];
        for (int i = 0; i < stats.length; i++) {
            stats[i] = new Column();
        }
    }

    /** Attaches the scratch database ({@code file}, or memory when null) and registers the key_value function. */
    static void attach(Connection conn, Path file) throws SQLException {
        try (PreparedStatement attach = conn.prepareStatement("ATTACH DATABASE ? AS " + SCHEMA)) {
            attach.setString(1, file == null ? ":memory:" : file.toAbsolutePath().toString());
            attach.execute();
        }
        try (Statement st = conn.createStatement()) {
            // Thrown away when the import ends, so there's nothing to protect with a journal or fsyncs.
            st.execute("PRAGMA " + SCHEMA + ".journal_mode = OFF");
            st.execute("PRAGMA " + SCHEMA + ".synchronous = OFF");
            st.execute("PRAGMA " + SCHEMA + ".cache_size = -131072"); // 128 MB, for building key indexes
        }
        Function.create(conn, "key_value", new Function() {
            @Override
            protected void xFunc() throws SQLException {
                String value = value_text(0);
                Object key = value == null ? null : keyValue(value);
                if (key == null) {
                    result();
                } else if (key instanceof Long number) {
                    result(number);
                } else {
                    result((String) key);
                }
            }
        }, 1, Function.FLAG_DETERMINISTIC);
    }

    static StagedTable create(Connection conn, int number, String name, List<String> columns) throws SQLException {
        StagedTable table = new StagedTable(number, name, columns);
        List<String> defs = new ArrayList<>();
        for (int c = 0; c < columns.size(); c++) {
            defs.add(table.column(c)); // no declared type, so every value stays exactly the text that was read
        }
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE " + table.table() + " (" + String.join(", ", defs) + ")");
        }
        table.insert = conn.prepareStatement("INSERT INTO " + table.table() + " VALUES ("
                + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")");
        return table;
    }

    static StagedTable of(Connection conn, int number, CsvTable source) throws SQLException {
        StagedTable table = create(conn, number, source.name(), source.columns());
        for (List<String> row : source.rows()) {
            table.add(row);
        }
        table.finish();
        return table;
    }

    /** Adds one row, the same width as {@link #columns}, with null for an empty value. */
    void add(List<String> row) throws SQLException {
        observe(row);
        write(row);
    }

    /**
     * The half of {@link #add} that learns about the values. It can run on another thread than {@link #write}, as
     * long as every row is observed once, in order, and that thread is finished before the table is analysed.
     */
    void observe(List<String> row) {
        for (int c = 0; c < stats.length; c++) {
            stats[c].add(row.get(c), observed);
        }
        observed++;
    }

    /** The half of {@link #add} that stores the row. */
    void write(List<String> row) throws SQLException {
        for (int c = 0; c < stats.length; c++) {
            insert.setString(c + 1, row.get(c));
        }
        insert.addBatch();
        rows++;
        if (++pending == BATCH_SIZE) {
            insert.executeBatch();
            pending = 0;
        }
    }

    void finish() throws SQLException {
        if (insert != null) {
            insert.executeBatch();
            insert.close();
            insert = null;
        }
        for (Column column : stats) {
            column.seen = null;
        }
    }

    long rows() {
        return rows;
    }

    /** The table in the scratch database, ready to go in SQL. */
    String table() {
        return SCHEMA + ".\"" + id + "\"";
    }

    String column(int c) {
        return "\"c" + c + "\"";
    }

    long nonNull(int c) {
        return rows - stats[c].nulls;
    }

    /**
     * INTEGER if every value is a whole number, REAL if every value is a number, otherwise TEXT. A number with a
     * leading zero (a zip code, "007") keeps the column TEXT so the zero isn't lost.
     */
    String type(int c) {
        Column column = stats[c];
        if (column.text || column.nulls == rows) {
            return "TEXT";
        }
        return column.integer ? "INTEGER" : "REAL";
    }

    /** False once the column is known not to be a key: a value is missing, or the sampled rows repeat a value. */
    boolean mayBeKey(int c) {
        return rows > 0 && stats[c].nulls == 0 && !stats[c].repeats;
    }

    /** Up to {@link #SAMPLE_VALUES} distinct {@link #keyValue}s from the first {@link #SAMPLE_ROWS} rows. */
    Set<Object> sample(int c) {
        return Collections.unmodifiableSet(stats[c].sample);
    }

    /** True when {@link #sample} holds every distinct value in the column, not just the first ones. */
    boolean sampleComplete(int c) {
        return rows <= SAMPLE_ROWS && stats[c].sample.size() < SAMPLE_VALUES;
    }

    /** The column's values in the form SQL compares them by, matching {@link #keyValue} for every value. */
    String keyExpression(int c) {
        String col = column(c);
        if (type(c).equals("INTEGER")) {
            return "CAST(" + col + " AS INTEGER)";
        }
        // Only text with a digit in it can be a number; the rest just needs trimming, which SQLite does natively.
        return "CASE WHEN " + col + " GLOB '*[0-9]*' THEN key_value(" + col + ") ELSE trim(" + col + ", "
                + SQL_TRIM_CHARS + ") END";
    }

    /** The column's values as they go into the final table. */
    String typedExpression(int c) {
        return switch (type(c)) {
            case "INTEGER" -> "CAST(" + column(c) + " AS INTEGER)";
            case "REAL" -> "CAST(" + column(c) + " AS REAL)";
            default -> column(c);
        };
    }

    /**
     * A value tidied for comparing: trimmed, and a plain number written one way, so "007", "7.0" and "7" are all
     * the whole number 7. Whole numbers come back as a Long so they equal an INTEGER column's values in SQL.
     */
    static Object keyValue(String value) {
        String t = trimKey(value);
        if (isPlainNumber(t)) {
            BigDecimal number = new BigDecimal(t).stripTrailingZeros();
            if (number.scale() <= 0 && number.precision() - number.scale() <= 18) {
                return number.longValueExact();
            }
            return number.toPlainString();
        }
        return t;
    }

    private static String trimKey(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && TRIM_CHARS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && TRIM_CHARS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    /** [-+]?\d+(\.\d+)? */
    private static boolean isPlainNumber(String s) {
        int i = s.isEmpty() || (s.charAt(0) != '-' && s.charAt(0) != '+') ? 0 : 1;
        int digits = skipDigits(s, i);
        if (digits == i) {
            return false;
        }
        if (digits == s.length()) {
            return true;
        }
        if (s.charAt(digits) != '.') {
            return false;
        }
        int fraction = skipDigits(s, digits + 1);
        return fraction > digits + 1 && fraction == s.length();
    }

    /** [-+]?\d{1,18}, so the value fits in a long. */
    static boolean isInteger(String s) {
        int i = s.isEmpty() || (s.charAt(0) != '-' && s.charAt(0) != '+') ? 0 : 1;
        int end = skipDigits(s, i);
        return end == s.length() && end > i && end - i <= 18;
    }

    /** The same numbers as {@link ChartMaker#DECIMAL}: [-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?, without a regex. */
    static boolean isDecimal(String s) {
        int i = s.isEmpty() || (s.charAt(0) != '-' && s.charAt(0) != '+') ? 0 : 1;
        int whole = skipDigits(s, i);
        int end = whole;
        if (end < s.length() && s.charAt(end) == '.') {
            end = skipDigits(s, end + 1);
            if (whole == i && end == whole + 1) {
                return false; // just "." with no digits either side
            }
        } else if (whole == i) {
            return false;
        }
        if (end < s.length() && (s.charAt(end) == 'e' || s.charAt(end) == 'E')) {
            int exponent = end + 1;
            if (exponent < s.length() && (s.charAt(exponent) == '-' || s.charAt(exponent) == '+')) {
                exponent++;
            }
            end = skipDigits(s, exponent);
            if (end == exponent) {
                return false;
            }
        }
        return end == s.length();
    }

    private static int skipDigits(String s, int from) {
        int i = from;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
        }
        return i;
    }

    /** What's been learned about one column so far. Runs on every value, so it avoids regexes and allocations. */
    private static final class Column {
        long nulls;
        boolean integer = true;
        boolean real = true;
        boolean text;
        boolean repeats;
        Set<Object> seen = new HashSet<>();
        final Set<Object> sample = new LinkedHashSet<>();

        void add(String value, long row) {
            if (value == null) {
                nulls++;
                return;
            }
            if (!text && value.isEmpty()) {
                text = true;
            } else if (!text) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if (first <= ' ' || last <= ' ' || (value.length() > 1 && first == '0' && value.charAt(1) != '.')) {
                    text = true;
                } else if (!(integer && isInteger(value))) {
                    // Every integer is also a decimal, so the decimal check is only needed once integers are out.
                    integer = false;
                    real = isDecimal(value);
                    text = !real;
                }
            }
            if (row < SAMPLE_ROWS) {
                Object key = keyValue(value);
                if (seen != null && !seen.add(key)) {
                    repeats = true;
                    seen = null;
                }
                if (sample.size() < SAMPLE_VALUES) {
                    sample.add(key);
                }
            } else if (seen != null) {
                seen = null; // the sample is done; nothing more to learn from it
            }
        }
    }
}

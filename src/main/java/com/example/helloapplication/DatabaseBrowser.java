package com.example.helloapplication;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public class DatabaseBrowser {

    public record TableRef(String schema, String name) {
        public String displayName() {
            return schema == null ? name : schema + "." + name;
        }
    }

    public record TablePreview(List<String> columns, List<List<String>> rows, long totalRows) {}

    public record ForeignKey(TableRef child, String childColumn, TableRef parent, String parentColumn) {}

    public record JoinPreview(TablePreview preview, long unmatchedRows) {}

    private static final String SQLITE_OPEN_READONLY = "1";
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final Path file;
    private final String url;
    private final Properties properties = new Properties();
    private List<TableRef> tables;
    private List<ForeignKey> foreignKeys;

    private DatabaseBrowser(Path file) {
        this.file = file;
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
        properties.setProperty("open_mode", SQLITE_OPEN_READONLY);
    }

    public static DatabaseBrowser sqlite(Path file) {
        return new DatabaseBrowser(file);
    }

    public String description() {
        return file.getFileName().toString();
    }

    public Path file() {
        return file;
    }

    Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }

    public synchronized List<TableRef> listTables() throws SQLException {
        if (tables == null) {
            tables = readTables();
        }
        return tables;
    }

    private List<TableRef> readTables() throws SQLException {
        List<TableRef> tables = new ArrayList<>();
        try (Connection conn = openConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (!name.startsWith("sqlite_")) {
                        tables.add(new TableRef(null, name));
                    }
                }
            }
        }
        tables.sort(Comparator.comparing(TableRef::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(tables);
    }

    public List<String> listColumns(TableRef table) throws SQLException {
        if (!listTables().contains(table)) {
            throw new IllegalArgumentException("No such table: " + table.displayName());
        }
        try (Connection conn = openConnection()) {
            String quote = conn.getMetaData().getIdentifierQuoteString().trim();
            return columnNames(conn, qualifiedName(table, quote));
        }
    }

    public TablePreview preview(TableRef table, int limit) throws SQLException {
        if (!listTables().contains(table)) {
            throw new IllegalArgumentException("No such table: " + table.displayName());
        }

        try (Connection conn = openConnection()) {
            String quote = conn.getMetaData().getIdentifierQuoteString().trim();
            String qualified = qualifiedName(table, quote);

            long total;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + qualified)) {
                rs.next();
                total = rs.getLong(1);
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + qualified + " LIMIT ?")) {
                ps.setInt(1, limit);
                return readRows(ps, total);
            }
        }
    }

    public TablePreview query(String sql, int limit) throws SQLException {
        String select = checkQuery(sql);
        try (Connection conn = openConnection()) {
            long total;
            try (Statement st = conn.createStatement()) {
                st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM (" + select + "\n)")) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM (" + select + "\n) LIMIT ?")) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setInt(1, limit);
                return readRows(ps, total);
            }
        }
    }

    static String checkQuery(String sql) {
        String text = sql == null ? "" : stripEdges(sql);
        while (text.endsWith(";")) {
            text = stripEdges(text.substring(0, text.length() - 1));
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Type a query first, for example: SELECT * FROM my_table");
        }
        if (hasSemicolonOutsideQuotes(text)) {
            throw new IllegalArgumentException("Run one query at a time.");
        }
        if (!text.toUpperCase(Locale.ROOT).matches("(?s)(SELECT|WITH)\\b.*")) {
            throw new IllegalArgumentException("Only SELECT queries can be run here; the data can't be changed.");
        }
        return text;
    }

    private static String stripEdges(String sql) {
        String text = sql.strip();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (text.startsWith("--")) {
                int end = text.indexOf('\n');
                text = end < 0 ? "" : text.substring(end + 1).strip();
                changed = true;
            } else if (text.startsWith("/*")) {
                int end = text.indexOf("*/", 2);
                text = end < 0 ? "" : text.substring(end + 2).strip();
                changed = true;
            }
            // A trailing line comment would swallow the ")" the query is wrapped in, so drop it.
            int lastLine = text.lastIndexOf('\n') + 1;
            int comment = commentStart(text, lastLine);
            if (comment >= 0) {
                text = text.substring(0, comment).strip();
                changed = true;
            }
        }
        return text;
    }

    private static int commentStart(String text, int from) {
        char quote = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasSemicolonOutsideQuotes(String text) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`' || c == '[') {
                quote = c == '[' ? ']' : c;
            } else if (c == ';') {
                return true;
            }
        }
        return false;
    }

    public synchronized List<ForeignKey> listForeignKeys() throws SQLException {
        if (foreignKeys == null) {
            foreignKeys = readForeignKeys();
        }
        return foreignKeys;
    }

    private List<ForeignKey> readForeignKeys() throws SQLException {
        List<TableRef> tables = listTables();
        List<ForeignKey> keys = new ArrayList<>();
        try (Connection conn = openConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            for (TableRef child : tables) {
                List<String[]> rows = new ArrayList<>();
                Set<String> multiColumn = new HashSet<>();
                try (ResultSet rs = meta.getImportedKeys(null, null, child.name())) {
                    while (rs.next()) {
                        String group = rs.getString("PKTABLE_NAME") + "|" + rs.getString("FK_NAME");
                        if (rs.getInt("KEY_SEQ") > 1) {
                            multiColumn.add(group);
                        }
                        rows.add(new String[]{group, rs.getString("PKTABLE_NAME"),
                                rs.getString("PKCOLUMN_NAME"), rs.getString("FKCOLUMN_NAME")});
                    }
                }
                for (String[] row : rows) {
                    TableRef parent = new TableRef(null, row[1]);
                    if (!multiColumn.contains(row[0]) && tables.contains(parent)
                            && row[2] != null && !row[2].isEmpty()) {
                        keys.add(new ForeignKey(child, row[3], parent, row[2]));
                    }
                }
            }
        }
        keys.sort(Comparator.comparing((ForeignKey k) -> k.child().displayName())
                .thenComparing(k -> k.parent().displayName())
                .thenComparing(ForeignKey::childColumn));
        return List.copyOf(keys);
    }

    public JoinPreview previewJoin(ForeignKey key, int limit) throws SQLException {
        if (!listForeignKeys().contains(key)) {
            throw new IllegalArgumentException("No such relationship: " + key);
        }
        try (Connection conn = openConnection()) {
            String quote = conn.getMetaData().getIdentifierQuoteString().trim();
            String child = qualifiedName(key.child(), quote);
            String parent = qualifiedName(key.parent(), quote);
            String on = "c." + quoteIdentifier(key.childColumn(), quote)
                    + " = p." + quoteIdentifier(key.parentColumn(), quote);

            List<String> select = new ArrayList<>();
            for (String column : columnNames(conn, child)) {
                select.add("c." + quoteIdentifier(column, quote) + " AS "
                        + quoteIdentifier(key.child().name() + "." + column, quote));
            }
            for (String column : columnNames(conn, parent)) {
                // A self-reference joins a table to itself, so the parent side needs its own label.
                String label = (key.parent().equals(key.child()) ? "parent " : "") + key.parent().name() + "." + column;
                select.add("p." + quoteIdentifier(column, quote) + " AS " + quoteIdentifier(label, quote));
            }

            long total;
            long unmatched;
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + child)) {
                    rs.next();
                    total = rs.getLong(1);
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + child + " c WHERE c."
                        + quoteIdentifier(key.childColumn(), quote) + " IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "
                        + parent + " p WHERE " + on + ")")) {
                    rs.next();
                    unmatched = rs.getLong(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT " + String.join(", ", select)
                    + " FROM " + child + " c LEFT JOIN " + parent + " p ON " + on + " LIMIT ?")) {
                ps.setInt(1, limit);
                return new JoinPreview(readRows(ps, total), unmatched);
            }
        }
    }

    private static TablePreview readRows(PreparedStatement ps, long total) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i));
            }
            while (rs.next()) {
                List<String> row = new ArrayList<>(columns.size());
                for (int i = 1; i <= columns.size(); i++) {
                    row.add(displayText(rs.getObject(i)));
                }
                rows.add(row);
            }
        }
        return new TablePreview(columns, rows, total);
    }

    private static List<String> columnNames(Connection conn, String qualifiedTable) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + qualifiedTable + " LIMIT 0")) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                names.add(meta.getColumnName(i));
            }
        }
        return names;
    }

    static String qualifiedName(TableRef table, String quote) {
        String name = quoteIdentifier(table.name(), quote);
        return table.schema() == null ? name : quoteIdentifier(table.schema(), quote) + "." + name;
    }

    static String displayText(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof byte[] bytes) {
            return "BLOB (" + bytes.length + " bytes)";
        }
        return value.toString();
    }

    static String quoteIdentifier(String name, String quote) {
        return quote + name.replace(quote, quote + quote) + quote;
    }
}

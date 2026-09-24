package com.example.helloapplication;

import com.example.helloapplication.DatabaseBrowser.ForeignKey;
import com.example.helloapplication.DatabaseBrowser.TableRef;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Links an admin adds by hand, for columns whose names don't say they're related (orders.cust_no pointing at
 * customers.customer_id). The automatic check (RelationshipFinder) only links columns whose names agree.
 */
final class LinkEditor {

    private static final String Q = "\"";

    /** What a proposed link would look like, before it's saved. */
    record Check(long distinct, long matched, boolean parentIsKey, boolean parentRepeats, String existingLink) {

        boolean canSave() {
            return existingLink == null && !parentRepeats && distinct > 0;
        }
    }

    private LinkEditor() {
    }

    /** Counts how well the child column's values match the parent column, on a read-only connection. */
    static Check check(DatabaseBrowser browser, ForeignKey link) throws SQLException {
        for (ForeignKey existing : browser.listForeignKeys()) {
            if (existing.child().equals(link.child()) && existing.childColumn().equals(link.childColumn())) {
                return new Check(0, 0, false, false, describe(existing));
            }
        }
        try (Connection conn = browser.openConnection()) {
            String child = DatabaseBrowser.qualifiedName(link.child(), Q);
            String parent = DatabaseBrowser.qualifiedName(link.parent(), Q);
            String childColumn = DatabaseBrowser.quoteIdentifier(link.childColumn(), Q);
            String parentColumn = DatabaseBrowser.quoteIdentifier(link.parentColumn(), Q);

            boolean parentIsKey = isKey(conn, link.parent(), link.parentColumn());
            boolean parentRepeats = !parentIsKey && count(conn, "SELECT COUNT(" + parentColumn + ") - COUNT(DISTINCT "
                    + parentColumn + ") FROM " + parent) > 0;
            if (parentRepeats) {
                return new Check(0, 0, false, true, null);
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*), COALESCE(SUM(v IN (SELECT " + parentColumn
                         + " FROM " + parent + ")), 0) FROM (SELECT DISTINCT " + childColumn + " AS v FROM " + child
                         + " WHERE " + childColumn + " IS NOT NULL)")) {
                rs.next();
                return new Check(rs.getLong(1), rs.getLong(2), parentIsKey, false, null);
            }
        }
    }

    /**
     * Saves the link as a real foreign key. SQLite can't add one to an existing table, so the child table is
     * rebuilt with it (its indexes and triggers are put back), and the parent column gets a UNIQUE index if it
     * isn't a key yet, as SQLite requires. It all happens in one transaction: on any failure nothing changes.
     */
    static void addLink(Path database, ForeignKey link, DataImporter.Progress progress) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA busy_timeout = 5000");
                st.execute("PRAGMA foreign_keys = OFF"); // only changeable outside a transaction
                // Otherwise renaming the rebuilt table fails if a view mentions it while the old one is gone.
                st.execute("PRAGMA legacy_alter_table = ON");
                st.execute("PRAGMA cache_size = -131072");
            }
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                String parent = DatabaseBrowser.qualifiedName(link.parent(), Q);
                if (!isKey(conn, link.parent(), link.parentColumn())) {
                    progress.update("Making " + link.parent().displayName() + "." + link.parentColumn() + " a key…");
                    String index = uniqueName(conn, "index", link.parent().name() + "_" + link.parentColumn() + "_key");
                    st.execute("CREATE UNIQUE INDEX " + DatabaseBrowser.quoteIdentifier(index, Q) + " ON " + parent
                            + " (" + DatabaseBrowser.quoteIdentifier(link.parentColumn(), Q) + ")");
                }

                String name = link.child().name();
                progress.update("Adding the link: rebuilding " + link.child().displayName()
                        + ". This takes a while for big tables…");
                String createSql = schemaSql(conn, "table", name).get(0);
                List<String> extras = new ArrayList<>(schemaSql(conn, "index", name));
                extras.addAll(schemaSql(conn, "trigger", name));
                String rebuilt = uniqueName(conn, "table", "new_" + name);
                String quotedRebuilt = DatabaseBrowser.quoteIdentifier(rebuilt, Q);
                String clause = "FOREIGN KEY (" + DatabaseBrowser.quoteIdentifier(link.childColumn(), Q)
                        + ") REFERENCES " + DatabaseBrowser.quoteIdentifier(link.parent().name(), Q)
                        + " (" + DatabaseBrowser.quoteIdentifier(link.parentColumn(), Q) + ")";

                st.execute(withForeignKey(renamed(createSql, quotedRebuilt), clause));
                st.execute("INSERT INTO " + quotedRebuilt + " SELECT * FROM " + DatabaseBrowser.quoteIdentifier(name, Q));
                st.execute("DROP TABLE " + DatabaseBrowser.quoteIdentifier(name, Q));
                st.execute("ALTER TABLE " + quotedRebuilt + " RENAME TO " + DatabaseBrowser.quoteIdentifier(name, Q));
                for (String sql : extras) {
                    st.execute(sql);
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }

            conn.setAutoCommit(true);
            // The old copy's pages are now free space; give it back when it's a real share of the file.
            if (count(conn, "PRAGMA freelist_count") * 4 > count(conn, "PRAGMA page_count")) {
                progress.update("Compacting " + database.getFileName() + "…");
                try (Statement st = conn.createStatement()) {
                    st.execute("VACUUM");
                }
            }
        }
    }

    static String describe(ForeignKey link) {
        return link.child().displayName() + "." + link.childColumn() + " → " + link.parent().displayName() + "."
                + link.parentColumn();
    }

    /** What the dialog says about a checked link. */
    static String describeCheck(ForeignKey link, Check check) {
        String child = link.childColumn();
        String parent = link.parent().displayName() + "." + link.parentColumn();
        if (check.existingLink() != null) {
            return child + " is already linked: " + check.existingLink() + ".";
        }
        if (check.parentRepeats()) {
            return parent + " has repeated values, so it can't be what " + child + " points at. A link needs a "
                    + "column with a different value in every row, such as an id.";
        }
        if (check.distinct() == 0) {
            return child + " has no values to link.";
        }
        long percent = check.matched() * 100 / check.distinct();
        String counts = String.format("%,d of %,d distinct %s values (%d%%) are in %s.", check.matched(),
                check.distinct(), child, percent, parent);
        if (check.matched() == 0) {
            return counts + " None match, so the joined view would show nothing. Check that it's the right column"
                    + " and that both are stored the same way (for example 007 as text vs 7 as a number).";
        }
        if (check.matched() < check.distinct()) {
            return counts + " The rest will show as rows that point at nothing.";
        }
        return counts;
    }

    // ---- SQL text ----

    /** CREATE TABLE text with the table's name swapped for {@code quotedName}. */
    static String renamed(String createSql, String quotedName) {
        int open = openParen(createSql);
        return "CREATE TABLE " + quotedName + " " + createSql.substring(open);
    }

    /** CREATE TABLE text with one more table constraint at the end of its definition list. */
    static String withForeignKey(String createSql, String clause) {
        int close = closeParen(createSql, openParen(createSql));
        String before = createSql.substring(0, close).stripTrailing();
        if (endsInLineComment(before)) {
            before += "\n"; // or the comma would be part of the comment
        }
        return before + ",\n    " + clause + "\n" + createSql.substring(close);
    }

    private static boolean endsInLineComment(String sql) {
        for (int i = sql.lastIndexOf('\n') + 1; i < sql.length(); i = skip(sql, i)) {
            if (sql.startsWith("--", i)) {
                return true;
            }
        }
        return false;
    }

    private static int openParen(String sql) {
        for (int i = 0; i < sql.length(); i = skip(sql, i)) {
            if (sql.charAt(i) == '(') {
                return i;
            }
        }
        throw new IllegalArgumentException("Not a CREATE TABLE statement: " + sql);
    }

    private static int closeParen(String sql, int open) {
        int depth = 0;
        for (int i = open; i < sql.length(); i = skip(sql, i)) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unbalanced CREATE TABLE statement: " + sql);
    }

    /** The index just past sql[i], treating a quoted name, a string or a comment as one step. */
    private static int skip(String sql, int i) {
        char c = sql.charAt(i);
        char end = switch (c) {
            case '"', '\'', '`' -> c;
            case '[' -> ']';
            default -> 0;
        };
        if (end != 0) {
            int close = sql.indexOf(end, i + 1);
            return close < 0 ? sql.length() : close + 1; // a doubled quote is just two quoted runs in a row
        }
        if (sql.startsWith("--", i)) {
            int line = sql.indexOf('\n', i);
            return line < 0 ? sql.length() : line + 1;
        }
        if (sql.startsWith("/*", i)) {
            int close = sql.indexOf("*/", i + 2);
            return close < 0 ? sql.length() : close + 2;
        }
        return i + 1;
    }

    // ---- schema ----

    /** True if the column alone is the table's primary key or has a UNIQUE index, as a foreign key needs. */
    static boolean isKey(Connection conn, TableRef table, String column) throws SQLException {
        String name = DatabaseBrowser.qualifiedName(table, Q);
        List<String> primaryKey = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + name + ")")) {
            while (rs.next()) {
                if (rs.getInt("pk") > 0) {
                    primaryKey.add(rs.getString("name"));
                }
            }
        }
        if (primaryKey.equals(List.of(column))) {
            return true;
        }
        List<String> uniqueIndexes = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_list(" + name + ")")) {
            while (rs.next()) {
                if (rs.getInt("unique") == 1) {
                    uniqueIndexes.add(rs.getString("name"));
                }
            }
        }
        for (String index : uniqueIndexes) {
            List<String> columns = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA index_info(" + DatabaseBrowser.quoteIdentifier(index, Q) + ")")) {
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
            }
            if (columns.equals(List.of(column))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> schemaSql(Connection conn, String type, String table) throws SQLException {
        List<String> sql = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT sql FROM sqlite_master WHERE type = ? AND "
                + (type.equals("table") ? "name" : "tbl_name") + " = ? AND sql IS NOT NULL")) {
            ps.setString(1, type);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sql.add(rs.getString(1));
                }
            }
        }
        if (type.equals("table") && sql.isEmpty()) {
            throw new SQLException("No table named " + table);
        }
        return sql;
    }

    private static String uniqueName(Connection conn, String type, String base) throws SQLException {
        String name = base;
        for (int n = 2; count(conn, "SELECT COUNT(*) FROM sqlite_master WHERE type = '" + type + "' AND name = "
                + DatabaseBrowser.quoteIdentifier(name, "'")) > 0; n++) {
            name = base + "_" + n;
        }
        return name;
    }

    private static long count(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}

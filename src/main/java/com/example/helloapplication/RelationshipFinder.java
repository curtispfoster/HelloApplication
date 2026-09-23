package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RelationshipFinder {

    public enum Confidence { STRONG, LIKELY, POSSIBLE }

    public record Relationship(String childTable, String childColumn, String parentTable, String parentColumn,
                               int matchedValues, int distinctValues, Confidence confidence) {
        public double matchRate() {
            return distinctValues == 0 ? 0 : (double) matchedValues / distinctValues;
        }
    }

    /** The links found, plus the key checks made along the way, which DataImporter reuses to pick primary keys. */
    record Analysis(List<Relationship> relationships, Keys keys) {}

    static final double NAMED_MIN_MATCH = 0.9;
    static final int VALUES_ONLY_MIN_DISTINCT = 10;

    private RelationshipFinder() {
    }

    /** Finds links between tables held in memory, by staging them in an in-memory database first. */
    public static List<Relationship> find(List<CsvTable> tables) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            StagedTable.attach(conn, null);
            List<StagedTable> staged = new ArrayList<>();
            for (CsvTable table : tables) {
                staged.add(StagedTable.of(conn, staged.size() + 1, table));
            }
            return find(conn, staged).relationships();
        } catch (SQLException e) {
            throw new IllegalStateException("Couldn't compare the tables.", e);
        }
    }

    /**
     * Finds links between staged tables. The counting is done by SQLite, so tables of any size can be compared;
     * the samples each StagedTable kept rule out most column/key pairs before any full count is needed.
     */
    static Analysis find(Connection conn, List<StagedTable> tables) throws SQLException {
        Keys keys = new Keys(conn);
        List<Relationship> found = new ArrayList<>();
        for (StagedTable child : tables) {
            for (int c = 0; c < child.columns.size(); c++) {
                String column = child.columns.get(c);
                if (isOwnId(child.name, column) || child.nonNull(c) == 0) {
                    continue;
                }
                Relationship best = null;
                for (StagedTable parent : tables) {
                    for (int k = 0; k < parent.columns.size(); k++) {
                        Relationship candidate = evaluate(keys, child, c, parent, k);
                        if (candidate != null && (best == null || RANKING.compare(candidate, best) < 0)) {
                            best = candidate;
                        }
                    }
                }
                if (best != null) {
                    found.add(best);
                }
            }
        }
        Map<String, String> firstColumns = new LinkedHashMap<>();
        for (StagedTable table : tables) {
            if (!table.columns.isEmpty()) {
                firstColumns.put(table.name, table.columns.get(0));
            }
        }
        return new Analysis(dropMirrorImages(found, firstColumns), keys);
    }

    private static final Comparator<Relationship> RANKING = Comparator
            .comparing(Relationship::confidence)
            .thenComparing(Comparator.comparingDouble(Relationship::matchRate).reversed());

    private static Relationship evaluate(Keys keys, StagedTable child, int c, StagedTable parent, int k)
            throws SQLException {
        String childColumn = child.columns.get(c);
        String parentColumn = parent.columns.get(k);
        boolean self = child == parent;
        if ((self && c == k) || !parent.mayBeKey(k)) {
            return null;
        }
        boolean named = self
                ? isSelfReferenceName(childColumn) && isOwnId(parent.name, parentColumn)
                : namesAgree(childColumn, parent.name, parentColumn);
        if (!named) {
            // Values alone: never within one table, never for a plain integer key (small ids overlap by
            // coincidence), and only when every sampled value is in the key, since the rate must be 100%.
            if (self || parent.type(k).equals("INTEGER")
                    || (child.sampleComplete(c) && child.sample(c).size() < VALUES_ONLY_MIN_DISTINCT)
                    || !keys.isKey(parent, k) || keys.allIntegers(parent, k)
                    || !keys.containsAll(parent, k, child.sample(c))) {
                return null;
            }
        } else if (!keys.isKey(parent, k)) {
            return null;
        }

        long[] counts = keys.matches(child, c, parent, k);
        long distinct = counts[0];
        long matched = counts[1];
        if (distinct == 0 || (!named && distinct < VALUES_ONLY_MIN_DISTINCT)) {
            return null;
        }
        double rate = (double) matched / distinct;

        Confidence confidence;
        if (named && rate >= NAMED_MIN_MATCH) {
            confidence = rate == 1.0 ? Confidence.STRONG : Confidence.LIKELY;
        } else if (!named && rate == 1.0) {
            confidence = Confidence.POSSIBLE;
        } else {
            return null;
        }
        return new Relationship(child.name, childColumn, parent.name, parentColumn,
                (int) matched, (int) distinct, confidence);
    }

    /**
     * Which columns are keys: a value in every row and no repeats, after tidying (see {@link #normalize}). Checking
     * one builds an index of its values in the scratch database, which then answers every lookup against it, so
     * each column is only checked when a link or primary key needs it, and only once.
     */
    static final class Keys {
        private final Connection conn;
        private final Map<StagedTable, Map<Integer, String>> indexes = new HashMap<>();
        private int next;

        Keys(Connection conn) {
            this.conn = conn;
        }

        boolean isKey(StagedTable table, int c) throws SQLException {
            return index(table, c) != null;
        }

        /** The index table holding the column's distinct values, or null if the column isn't a key. */
        private String index(StagedTable table, int c) throws SQLException {
            Map<Integer, String> byColumn = indexes.computeIfAbsent(table, t -> new HashMap<>());
            if (byColumn.containsKey(c)) {
                return byColumn.get(c);
            }
            String index = null;
            if (table.mayBeKey(c)) {
                String candidate = StagedTable.SCHEMA + ".\"k" + (++next) + "\"";
                try (Statement st = conn.createStatement()) {
                    // No declared type on v, so whole numbers stay integers and text stays text, as keyValue made them.
                    st.execute("CREATE TABLE " + candidate + " (v PRIMARY KEY) WITHOUT ROWID");
                    st.execute("INSERT OR IGNORE INTO " + candidate + " SELECT " + table.keyExpression(c)
                            + " FROM " + table.table());
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + candidate)) {
                        rs.next();
                        if (rs.getLong(1) == table.rows()) {
                            index = candidate;
                        } else {
                            st.execute("DROP TABLE " + candidate);
                        }
                    }
                }
            }
            byColumn.put(c, index);
            return index;
        }

        /** True if every value is a whole number (as small ids are), in which case a values-only match means little. */
        boolean allIntegers(StagedTable table, int c) throws SQLException {
            if (table.type(c).equals("INTEGER")) {
                return true;
            }
            // Whole numbers too long for a 64-bit integer come back from key_value as text digits.
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT NOT EXISTS (SELECT 1 FROM " + index(table, c)
                         + " WHERE typeof(v) <> 'integer' AND NOT (ltrim(v, '-') GLOB '[0-9]*'"
                         + " AND ltrim(v, '-') NOT GLOB '*[^0-9]*'))")) {
                rs.next();
                return rs.getBoolean(1);
            }
        }

        boolean containsAll(StagedTable table, int c, Collection<Object> values) throws SQLException {
            if (values.isEmpty()) {
                return true;
            }
            List<Object> list = new ArrayList<>(values);
            String index = index(table, c);
            final int chunk = 500;
            for (int from = 0; from < list.size(); from += chunk) {
                List<Object> part = list.subList(from, Math.min(list.size(), from + chunk));
                String placeholders = String.join(", ", Collections.nCopies(part.size(), "?"));
                try (PreparedStatement st = conn.prepareStatement(
                        "SELECT COUNT(*) FROM " + index + " WHERE v IN (" + placeholders + ")")) {
                    for (int i = 0; i < part.size(); i++) {
                        st.setObject(i + 1, part.get(i));
                    }
                    try (ResultSet rs = st.executeQuery()) {
                        rs.next();
                        if (rs.getLong(1) < part.size()) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /** {distinct values in the child column, how many of them are in the key}. */
        long[] matches(StagedTable child, int c, StagedTable parent, int k) throws SQLException {
            String key = index(parent, k);
            // Collected into a table in the scratch database rather than with SELECT DISTINCT, whose sort runs in
            // SQLite's temp store with a small cache and took over a minute on 8 million rows.
            String distinct = StagedTable.SCHEMA + ".\"d" + (++next) + "\"";
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE " + distinct + " (v PRIMARY KEY) WITHOUT ROWID");
                st.execute("INSERT OR IGNORE INTO " + distinct + " SELECT " + child.keyExpression(c)
                        + " FROM " + child.table() + " WHERE " + child.column(c) + " IS NOT NULL");
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*), COALESCE(SUM(v IN (SELECT v FROM " + key
                        + ")), 0) FROM " + distinct)) {
                    rs.next();
                    return new long[]{rs.getLong(1), rs.getLong(2)};
                } finally {
                    st.execute("DROP TABLE " + distinct);
                }
            }
        }
    }

    private static List<Relationship> dropMirrorImages(List<Relationship> found, Map<String, String> firstColumns) {
        List<Relationship> kept = new ArrayList<>();
        for (Relationship r : found) {
            Relationship mirror = null;
            for (Relationship other : kept) {
                if (other.childTable().equals(r.parentTable()) && other.childColumn().equals(r.parentColumn())
                        && other.parentTable().equals(r.childTable()) && other.parentColumn().equals(r.childColumn())) {
                    mirror = other;
                }
            }
            if (mirror == null) {
                kept.add(r);
            } else if (ownership(r, firstColumns) > ownership(mirror, firstColumns)) {
                kept.set(kept.indexOf(mirror), r);
            }
        }
        return kept;
    }

    private static int ownership(Relationship r, Map<String, String> firstColumns) {
        if (isOwnId(r.parentTable(), r.parentColumn())) {
            return 2;
        }
        return r.parentColumn().equals(firstColumns.get(r.parentTable())) ? 1 : 0;
    }

    /** A value as it's compared: trimmed, and numbers written one way ("007", "7.0" and "7" are all "7"). */
    static String normalize(String value) {
        return String.valueOf(StagedTable.keyValue(value));
    }

    static boolean namesAgree(String childColumn, String parentTable, String parentColumn) {
        String c = simplify(childColumn);
        String p = simplify(parentColumn);
        String table = singular(simplify(parentTable));
        if (c.equals(p)) {
            // A bare "id" or "name" on both sides says nothing; a specific name like customer_id or sku does.
            return !GENERIC_NAMES.contains(p);
        }
        return c.equals(table + "_" + p) || c.equals(table + p)
                || (p.equals("id") && (c.equals(table + "_id") || c.equals(table + "id")));
    }

    private static final Set<String> GENERIC_NAMES = Set.of("id", "name", "title", "code", "key", "value", "type");

    static boolean isOwnId(String table, String column) {
        String c = simplify(column);
        String t = singular(simplify(table));
        return c.equals("id") || c.equals(t + "_id") || c.equals(t + "id");
    }

    private static boolean isSelfReferenceName(String column) {
        String c = simplify(column);
        return c.equals("parent_id") || c.equals("manager_id") || c.equals("reports_to")
                || c.equals("supervisor_id") || c.equals("parent");
    }

    static String simplify(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    static String singular(String name) {
        if (name.endsWith("ies") && name.length() > 3) {
            return name.substring(0, name.length() - 3) + "y";
        }
        if (name.endsWith("sses") || name.endsWith("xes") || name.endsWith("ches") || name.endsWith("shes")) {
            return name.substring(0, name.length() - 2);
        }
        // status, analysis, class: an -s that isn't a plural ending.
        if (name.endsWith("s") && !name.endsWith("ss") && !name.endsWith("us") && !name.endsWith("is")
                && name.length() > 1) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }
}

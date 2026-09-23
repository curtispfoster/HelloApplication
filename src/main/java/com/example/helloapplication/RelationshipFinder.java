package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class RelationshipFinder {

    public enum Confidence { STRONG, LIKELY, POSSIBLE }

    public record Relationship(String childTable, String childColumn, String parentTable, String parentColumn,
                               int matchedValues, int distinctValues, Confidence confidence) {
        public double matchRate() {
            return distinctValues == 0 ? 0 : (double) matchedValues / distinctValues;
        }
    }

    static final double NAMED_MIN_MATCH = 0.9;
    static final int VALUES_ONLY_MIN_DISTINCT = 10;

    // Compiled once: these run on every cell of every imported file.
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[-+]?\\d+(\\.\\d+)?");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d+");

    private RelationshipFinder() {
    }

    public static List<Relationship> find(List<CsvTable> tables) {
        Map<String, Map<String, Set<String>>> keysByTable = new LinkedHashMap<>();
        for (CsvTable table : tables) {
            keysByTable.put(table.name(), keyColumns(table));
        }

        List<Relationship> found = new ArrayList<>();
        for (CsvTable child : tables) {
            for (int c = 0; c < child.columns().size(); c++) {
                String column = child.columns().get(c);
                if (isOwnId(child.name(), column)) {
                    continue;
                }
                Set<String> values = distinctValues(child, c);
                if (values.isEmpty()) {
                    continue;
                }
                Relationship best = null;
                for (CsvTable parent : tables) {
                    for (Map.Entry<String, Set<String>> key : keysByTable.get(parent.name()).entrySet()) {
                        Relationship candidate = evaluate(child.name(), column, values,
                                parent.name(), key.getKey(), key.getValue());
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
        for (CsvTable table : tables) {
            if (!table.columns().isEmpty()) {
                firstColumns.put(table.name(), table.columns().get(0));
            }
        }
        return dropMirrorImages(found, firstColumns);
    }

    private static final Comparator<Relationship> RANKING = Comparator
            .comparing(Relationship::confidence)
            .thenComparing(Comparator.comparingDouble(Relationship::matchRate).reversed());

    private static Relationship evaluate(String childTable, String childColumn, Set<String> values,
                                         String parentTable, String parentColumn, Set<String> keyValues) {
        boolean self = childTable.equals(parentTable);
        if (self && childColumn.equals(parentColumn)) {
            return null;
        }
        boolean named = self
                ? isSelfReferenceName(childColumn) && isOwnId(parentTable, parentColumn)
                : namesAgree(childColumn, parentTable, parentColumn);
        if (!named && (self || values.size() < VALUES_ONLY_MIN_DISTINCT)) {
            return null;
        }
        // Most column/key pairs don't match at all, so stop counting as soon as this one can't qualify.
        int misses = 0;
        for (String value : values) {
            if (!keyValues.contains(value)) {
                misses++;
                if (!named || (double) (values.size() - misses) / values.size() < NAMED_MIN_MATCH) {
                    return null;
                }
            }
        }
        int matched = values.size() - misses;
        double rate = (double) matched / values.size();

        Confidence confidence;
        if (named && rate >= NAMED_MIN_MATCH) {
            confidence = rate == 1.0 ? Confidence.STRONG : Confidence.LIKELY;
        } else if (!named && !self && rate == 1.0 && values.size() >= VALUES_ONLY_MIN_DISTINCT
                && !allIntegers(keyValues)) {
            confidence = Confidence.POSSIBLE;
        } else {
            return null;
        }
        return new Relationship(childTable, childColumn, parentTable, parentColumn, matched, values.size(), confidence);
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

    static Map<String, Set<String>> keyColumns(CsvTable table) {
        Map<String, Set<String>> keys = new LinkedHashMap<>();
        if (table.rows().isEmpty()) {
            return keys;
        }
        for (int c = 0; c < table.columns().size(); c++) {
            Set<String> seen = new HashSet<>();
            boolean unique = true;
            for (List<String> row : table.rows()) {
                String value = row.get(c);
                if (value == null || !seen.add(normalize(value))) {
                    unique = false;
                    break;
                }
            }
            if (unique) {
                keys.put(table.columns().get(c), seen);
            }
        }
        return keys;
    }

    private static Set<String> distinctValues(CsvTable table, int column) {
        Set<String> values = new HashSet<>();
        for (List<String> row : table.rows()) {
            String value = row.get(column);
            if (value != null) {
                values.add(normalize(value));
            }
        }
        return values;
    }

    static String normalize(String value) {
        String v = value.trim();
        if (PLAIN_NUMBER.matcher(v).matches()) {
            try {
                return new BigDecimal(v).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return v;
    }

    private static boolean allIntegers(Set<String> values) {
        for (String v : values) {
            if (!WHOLE_NUMBER.matcher(v).matches()) {
                return false;
            }
        }
        return true;
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

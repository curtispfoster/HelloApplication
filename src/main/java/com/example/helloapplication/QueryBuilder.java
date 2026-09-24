package com.example.helloapplication;

import com.example.helloapplication.DatabaseBrowser.ForeignKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Writes the SELECT behind Home's point-and-click controls, so users can link, filter and sort
 * a table without knowing SQL. The result still goes through {@link DatabaseBrowser#query}, which
 * only runs single read-only SELECTs.
 */
public final class QueryBuilder {

    public enum Op {
        IS("is", true), IS_NOT("is not", true), CONTAINS("contains", true),
        GREATER("is more than", true), LESS("is less than", true),
        EMPTY("is empty", false), NOT_EMPTY("is not empty", false);

        final String label;
        final boolean needsValue;

        Op(String label, boolean needsValue) {
            this.label = label;
            this.needsValue = needsValue;
        }

        public boolean needsValue() {
            return needsValue;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Filter(String column, Op op, String value) {
        @Override
        public String toString() {
            return column + " " + op + (op.needsValue ? " " + value : "");
        }
    }

    /** {@code sortColumn} may be null for the table's own order. */
    public record Request(String table, List<ForeignKey> links, List<Filter> filters,
                          String sortColumn, boolean descending) {
        public Request {
            links = List.copyOf(links);
            filters = List.copyOf(filters);
        }

        public static Request of(String table) {
            return new Request(table, List.of(), List.of(), null, false);
        }
    }

    // A plain number with no leading zero, so zip codes like "02134" stay text.
    private static final Pattern PLAIN_NUMBER = Pattern.compile("-?(0|[1-9]\\d*)(\\.\\d+)?");

    private QueryBuilder() {
    }

    /** {@code columnsByTable} gives each linked table's columns, which are labelled "table.column". */
    public static String sql(Request request, Map<String, List<String>> columnsByTable) {
        String select = request.links().isEmpty()
                ? Home_View.starterQuery(request.table())
                : joined(request, columnsByTable);
        if (request.filters().isEmpty() && request.sortColumn() == null) {
            return select;
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM (\n").append(select).append("\n)");
        List<String> conditions = new ArrayList<>();
        for (Filter filter : request.filters()) {
            conditions.add(condition(filter));
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (request.sortColumn() != null) {
            sql.append(" ORDER BY ").append(quote(request.sortColumn())).append(request.descending() ? " DESC" : "");
        }
        return sql.toString();
    }

    /** The columns the query returns, in order: the table's own, then each link's labelled ones. */
    public static List<String> columns(Request request, Map<String, List<String>> columnsByTable) {
        List<String> columns = new ArrayList<>(columnsByTable.getOrDefault(request.table(), List.of()));
        for (ForeignKey link : request.links()) {
            for (String column : linkedColumns(link, columnsByTable)) {
                columns.add(linkName(link, request.links()) + "." + column);
            }
        }
        return columns;
    }

    // The linked table's key would only repeat the table's own column it was joined on.
    private static List<String> linkedColumns(ForeignKey link, Map<String, List<String>> columnsByTable) {
        return columnsByTable.getOrDefault(link.parent().name(), List.of()).stream()
                .filter(column -> !column.equals(link.parentColumn()))
                .toList();
    }

    /**
     * How a link's table is named in column labels and the link menu: just the table, unless two
     * chosen links reach the same table or a table links to itself.
     */
    public static String linkName(ForeignKey link, List<ForeignKey> links) {
        String parent = link.parent().name();
        if (link.parent().equals(link.child())) {
            return "parent " + parent;
        }
        long sameParent = links.stream().filter(other -> other.parent().equals(link.parent())).count();
        return sameParent > 1 ? parent + " (" + link.childColumn() + ")" : parent;
    }

    private static String joined(Request request, Map<String, List<String>> columnsByTable) {
        List<String> select = new ArrayList<>(List.of("t.*"));
        StringBuilder from = new StringBuilder(" FROM ").append(Home_View.sqlName(request.table())).append(" t");
        int n = 0;
        for (ForeignKey link : request.links()) {
            String alias = "p" + ++n;
            String name = linkName(link, request.links());
            for (String column : linkedColumns(link, columnsByTable)) {
                select.add(alias + "." + quote(column) + " AS " + quote(name + "." + column));
            }
            from.append("\nLEFT JOIN ").append(Home_View.sqlName(link.parent().name())).append(' ').append(alias)
                    .append(" ON t.").append(quote(link.childColumn()))
                    .append(" = ").append(alias).append('.').append(quote(link.parentColumn()));
        }
        return "SELECT " + String.join(", ", select) + from;
    }

    static String condition(Filter filter) {
        String column = quote(filter.column());
        String value = filter.value() == null ? "" : filter.value().strip();
        return switch (filter.op()) {
            case IS -> column + " = " + literal(value);
            case IS_NOT -> "(" + column + " IS NULL OR " + column + " <> " + literal(value) + ")";
            case CONTAINS -> column + " LIKE '%" + escapeLike(value).replace("'", "''") + "%' ESCAPE '\\'";
            case GREATER -> column + " > " + literal(value);
            case LESS -> column + " < " + literal(value);
            case EMPTY -> "(" + column + " IS NULL OR " + column + " = '')";
            case NOT_EMPTY -> "(" + column + " IS NOT NULL AND " + column + " <> '')";
        };
    }

    static String literal(String value) {
        return PLAIN_NUMBER.matcher(value).matches() ? value : "'" + value.replace("'", "''") + "'";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String quote(String column) {
        return DatabaseBrowser.quoteIdentifier(column, "\"");
    }
}

package com.example.helloapplication;

import com.example.helloapplication.CsvReader.CsvTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class JsonReader {

    /** The whole document is parsed in memory, so JSON files are capped. Large data should come as CSV. */
    public static final long MAX_BYTES = 200L * 1024 * 1024;

    private JsonReader() {
    }

    public static List<CsvTable> read(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) {
            throw new IOException(file.getFileName() + " is larger than " + MAX_BYTES / (1024 * 1024)
                    + " MB. Large data can be imported as CSV, which has no size limit.");
        }
        String text = CsvReader.decode(Files.readAllBytes(file)).strip();
        String fileName = file.getFileName().toString();
        if (text.isEmpty()) {
            throw new IOException(fileName + " is empty.");
        }

        String name = CsvReader.tableName(file);
        String lower = fileName.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".jsonl") || lower.endsWith(".ndjson")) {
                return List.of(toTable(name, parseLines(text)));
            }
            return tables(name, new Parser(text).parseDocument());
        } catch (IllegalArgumentException e) {
            throw new IOException(fileName + " isn't valid JSON: " + e.getMessage(), e);
        }
    }

    static List<CsvTable> tables(String fileTableName, Object document) {
        if (document instanceof List<?> list) {
            return List.of(toTable(fileTableName, list));
        }
        if (document instanceof Map<?, ?> map) {
            Map<String, List<?>> arrays = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof List<?> list && !list.isEmpty()
                        && list.stream().allMatch(v -> v instanceof Map)) {
                    arrays.put((String) entry.getKey(), list);
                }
            }
            if (arrays.size() == 1) {
                return List.of(toTable(fileTableName, arrays.values().iterator().next()));
            }
            if (arrays.size() > 1) {
                List<CsvTable> tables = new ArrayList<>();
                arrays.forEach((key, list) -> tables.add(toTable(CsvReader.safeName(key), list)));
                return tables;
            }
            return List.of(toTable(fileTableName, List.of(map)));
        }
        return List.of(toTable(fileTableName, List.of(document == null ? Parser.NULL : document)));
    }

    private static List<Object> parseLines(String text) {
        List<Object> values = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                try {
                    values.add(new Parser(lines[i]).parseDocument());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage());
                }
            }
        }
        return values;
    }

    private static CsvTable toTable(String name, List<?> items) {
        List<Map<String, String>> flatRows = new ArrayList<>(items.size());
        Set<String> keys = new LinkedHashSet<>();
        for (Object item : items) {
            Map<String, String> row = new LinkedHashMap<>();
            if (item instanceof Map<?, ?> object) {
                flatten("", object, row);
            } else {
                row.put("value", cellText(item));
            }
            keys.addAll(row.keySet());
            flatRows.add(row);
        }

        List<String> keyList = new ArrayList<>(keys);
        List<String> columns = CsvReader.uniqueColumnNames(keyList);
        if (columns.isEmpty()) {
            columns = List.of("value");
            keyList = List.of("value");
        }
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < keyList.size(); i++) {
            index.put(keyList.get(i), i);
        }

        List<List<String>> rows = new ArrayList<>(flatRows.size());
        for (Map<String, String> flat : flatRows) {
            List<String> row = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                row.add(null);
            }
            flat.forEach((key, value) -> row.set(index.get(key), value));
            rows.add(row);
        }
        return new CsvTable(name, columns, rows);
    }

    private static void flatten(String prefix, Map<?, ?> object, Map<String, String> row) {
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested && !nested.isEmpty()) {
                flatten(key + "_", nested, row);
            } else {
                row.put(key, cellText(entry.getValue()));
            }
        }
    }

    private static String cellText(Object value) {
        if (value == null || value == Parser.NULL) {
            return null;
        }
        if (value instanceof String s) {
            return s.isEmpty() ? null : s;
        }
        if (value instanceof JsonNumber number) {
            return number.text();
        }
        if (value instanceof Boolean || value instanceof Map || value instanceof List) {
            return toJson(value);
        }
        return value.toString();
    }

    static String toJson(Object value) {
        if (value == null || value == Parser.NULL) {
            return "null";
        }
        if (value instanceof String s) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            return out.append('"').toString();
        }
        if (value instanceof JsonNumber number) {
            return number.text();
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((k, v) -> parts.add(toJson(k) + ":" + toJson(v)));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(JsonReader::toJson).toList()) + "]";
        }
        return value.toString();
    }

    record JsonNumber(String text) {}

    static final class Parser {

        static final Object NULL = new Object() {
            @Override
            public String toString() {
                return "null";
            }
        };

        private static final int MAX_DEPTH = 500;

        private final String text;
        private int pos;
        private int depth;

        Parser(String text) {
            this.text = text;
        }

        Object parseDocument() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos < text.length()) {
                throw error("unexpected " + describe(text.charAt(pos)) + " after the end of the data");
            }
            return value;
        }

        private Object parseValue() {
            if (pos >= text.length()) {
                throw error("the data ends too early");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", NULL);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw error("unexpected " + describe(c));
                }
            };
        }

        private Map<String, Object> parseObject() {
            enter();
            pos++; // {
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                depth--;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected a quoted key");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                object.put(key, parseValue());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                } else if (peek() == '}') {
                    pos++;
                    depth--;
                    return object;
                } else {
                    throw error("expected , or } in an object");
                }
            }
        }

        private List<Object> parseArray() {
            enter();
            pos++; // [
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                depth--;
                return array;
            }
            while (true) {
                skipWhitespace();
                array.add(parseValue());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                } else if (peek() == ']') {
                    pos++;
                    depth--;
                    return array;
                } else {
                    throw error("expected , or ] in an array");
                }
            }
        }

        private String parseString() {
            pos++; // opening quote
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("a string is never closed");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (pos >= text.length()) {
                        throw error("a string is never closed");
                    }
                    char e = text.charAt(pos++);
                    switch (e) {
                        case '"', '\\', '/' -> out.append(e);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw error("incomplete \\u escape");
                            }
                            try {
                                out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("bad \\u escape");
                            }
                            pos += 4;
                        }
                        default -> throw error("unknown escape \\" + e);
                    }
                } else if (c < 0x20) {
                    throw error("a line break or control character inside a string");
                } else {
                    out.append(c);
                }
            }
        }

        private JsonNumber parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            int digits = skipDigits();
            if (digits == 0) {
                throw error("a number has no digits");
            }
            if (peek() == '.') {
                pos++;
                if (skipDigits() == 0) {
                    throw error("a number has no digits after its decimal point");
                }
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++;
                if (peek() == '+' || peek() == '-') {
                    pos++;
                }
                if (skipDigits() == 0) {
                    throw error("a number has no digits in its exponent");
                }
            }
            return new JsonNumber(text.substring(start, pos));
        }

        private int skipDigits() {
            int start = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos)) && text.charAt(pos) < 128) {
                pos++;
            }
            return pos - start;
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw error("unexpected " + describe(text.charAt(pos)));
            }
            pos += word.length();
            return value;
        }

        private void enter() {
            if (++depth > MAX_DEPTH) {
                throw error("nested more than " + MAX_DEPTH + " levels deep");
            }
        }

        private void expect(char c) {
            if (peek() != c) {
                throw error("expected " + c);
            }
            pos++;
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                pos++;
            }
        }

        private static String describe(char c) {
            return c == '\0' ? "end of data" : "'" + c + "'";
        }

        private IllegalArgumentException error(String message) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < Math.min(pos, text.length()); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new IllegalArgumentException(message + " (line " + line + ", column " + column + ")");
        }
    }
}

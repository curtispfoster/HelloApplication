package com.example.helloapplication;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CsvReader {

    public static final long MAX_BYTES = 200L * 1024 * 1024;

    public record CsvTable(String name, List<String> columns, List<List<String>> rows) {}

    private CsvReader() {
    }

    public static CsvTable read(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) {
            throw new IOException(file.getFileName() + " is larger than " + MAX_BYTES / (1024 * 1024) + " MB.");
        }
        String text = decode(Files.readAllBytes(file));
        List<List<String>> records = parse(text, detectDelimiter(text));
        if (records.isEmpty()) {
            throw new IOException(file.getFileName() + " is empty.");
        }

        List<String> columns = uniqueColumnNames(records.get(0));
        List<List<String>> rows = new ArrayList<>(records.size() - 1);
        for (List<String> record : records.subList(1, records.size())) {
            List<String> row = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                String value = i < record.size() ? record.get(i) : null;
                row.add(value == null || value.isEmpty() ? null : value);
            }
            rows.add(row);
        }
        return new CsvTable(tableName(file), columns, rows);
    }

    static String decode(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(bytes, start, bytes.length - start);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, start, bytes.length - start, Charset.forName("windows-1252"));
        }
    }

    static char detectDelimiter(String text) {
        int comma = 0;
        int semicolon = 0;
        int tab = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '\n' || c == '\r')) {
                break;
            } else if (!quoted) {
                if (c == ',') comma++;
                else if (c == ';') semicolon++;
                else if (c == '\t') tab++;
            }
        }
        if (tab > comma && tab >= semicolon) return '\t';
        if (semicolon > comma) return ';';
        return ',';
    }

    static List<List<String>> parse(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
                fieldStarted = true;
            } else if (c == delimiter) {
                record.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                endRecord(records, record, field, fieldStarted);
                record = new ArrayList<>();
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        endRecord(records, record, field, fieldStarted);
        return records;
    }

    private static void endRecord(List<List<String>> records, List<String> record, StringBuilder field,
                                  boolean fieldStarted) {
        if (!fieldStarted && record.isEmpty()) {
            return; // blank line
        }
        record.add(field.toString());
        field.setLength(0);
        records.add(record);
    }

    static List<String> uniqueColumnNames(List<String> header) {
        List<String> names = new ArrayList<>(header.size());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < header.size(); i++) {
            String base = header.get(i) == null ? "" : header.get(i).trim();
            if (base.isEmpty()) {
                base = "column_" + (i + 1);
            }
            String name = base;
            for (int n = 2; !seen.add(name.toLowerCase(Locale.ROOT)); n++) {
                name = base + "_" + n;
            }
            names.add(name);
        }
        return names;
    }

    static String tableName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return safeName(dot > 0 ? name.substring(0, dot) : name);
    }

    static String safeName(String text) {
        String name = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (name.isEmpty()) {
            name = "table";
        }
        return Character.isDigit(name.charAt(0)) ? "t_" + name : name;
    }
}

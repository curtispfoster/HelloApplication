package com.example.helloapplication;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
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

    /** A quoted value longer than this almost always means a quote that's never closed, not real data. */
    static final int MAX_FIELD_CHARS = 10_000_000;

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final int HEADER_SCAN_CHARS = 1 << 20;

    public record CsvTable(String name, List<String> columns, List<List<String>> rows) {}

    private CsvReader() {
    }

    /** Reads a whole file into memory. Imports use {@link #open} instead, which reads one row at a time. */
    public static CsvTable read(Path file) throws IOException {
        try (Rows rows = open(file)) {
            List<List<String>> all = new ArrayList<>();
            for (List<String> row = rows.next(); row != null; row = rows.next()) {
                all.add(row);
            }
            return new CsvTable(tableName(file), rows.columns(), all);
        }
    }

    /** Opens a file for reading row by row, so a file of any size is read in constant memory. */
    public static Rows open(Path file) throws IOException {
        return new Rows(file);
    }

    /** A CSV file being read one row at a time. Rows are padded or cut to the header's width; empty fields are null. */
    public static final class Rows implements Closeable {

        private final Path file;
        private final long totalBytes;
        private final CountingInputStream counter;
        private final BufferedReader reader;
        private final char delimiter;
        private final List<String> columns;
        private final StringBuilder field = new StringBuilder();
        private final char[] buffer = new char[1 << 16];
        private int position;
        private int limit;
        private long line = 1;

        private Rows(Path file) throws IOException {
            this.file = file;
            this.totalBytes = Files.size(file);
            Charset charset = isUtf8(file) ? StandardCharsets.UTF_8 : WINDOWS_1252;
            InputStream in = Files.newInputStream(file);
            try {
                counter = new CountingInputStream(in);
                if (charset == StandardCharsets.UTF_8) {
                    skipBom(counter);
                }
                reader = new BufferedReader(new InputStreamReader(counter, charset), 1 << 16);
                delimiter = detectDelimiter(peekHeader(reader));
                List<String> header = nextRecord();
                if (header == null) {
                    throw new IOException(file.getFileName() + " is empty.");
                }
                columns = uniqueColumnNames(header);
            } catch (IOException | RuntimeException e) {
                in.close();
                throw e;
            }
        }

        public List<String> columns() {
            return columns;
        }

        /** The next row, or null at the end of the file. */
        public List<String> next() throws IOException {
            List<String> record = nextRecord();
            if (record == null) {
                return null;
            }
            int width = columns.size();
            if (record.size() > width) {
                record.subList(width, record.size()).clear();
            }
            for (int i = 0; i < record.size(); i++) {
                if (record.get(i).isEmpty()) {
                    record.set(i, null);
                }
            }
            while (record.size() < width) {
                record.add(null);
            }
            return record;
        }

        public long bytesRead() {
            return counter.count;
        }

        public long totalBytes() {
            return totalBytes;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }

        // RFC 4180: quoted fields can hold the delimiter, line breaks and doubled quotes. Blank lines are skipped.
        private List<String> nextRecord() throws IOException {
            while (true) {
                if (position == limit && !fill()) {
                    return null;
                }
                char c = buffer[position];
                if (c != '\n' && c != '\r') {
                    break;
                }
                endLine(c); // blank line
            }
            List<String> record = new ArrayList<>(columns == null ? 16 : columns.size() + 1);
            while (readField(record)) {
                // until the end of the line or file
            }
            return record;
        }

        /**
         * Adds the next field to the record; true if a delimiter followed it. An unquoted field is cut straight out
         * of the buffer, which is what keeps big files fast; the builder is only used for quotes and buffer edges.
         */
        private boolean readField(List<String> record) throws IOException {
            field.setLength(0);
            int start = position;
            while (true) {
                if (position == limit) {
                    field.append(buffer, start, position - start);
                    if (!fill()) {
                        record.add(field.toString());
                        return false;
                    }
                    start = position;
                }
                char c = buffer[position];
                if (c == delimiter || c == '\n' || c == '\r') {
                    int length = position - start;
                    if (field.isEmpty()) {
                        record.add(length == 0 ? "" : new String(buffer, start, length));
                    } else {
                        record.add(field.append(buffer, start, length).toString());
                    }
                    if (c == delimiter) {
                        position++;
                        return true;
                    }
                    endLine(c);
                    return false;
                }
                if (c == '"' && position == start && field.isEmpty()) {
                    position++;
                    readQuoted();
                    start = position;
                    continue;
                }
                position++;
            }
        }

        /** Reads a quoted section into the builder, up to and past its closing quote. */
        private void readQuoted() throws IOException {
            long quoteLine = line;
            while (true) {
                if (position == limit && !fill()) {
                    throw unclosedQuote(quoteLine);
                }
                char c = buffer[position++];
                if (c == '"') {
                    if (peek() != '"') {
                        return;
                    }
                    position++;
                }
                if (c == '\n') {
                    line++;
                }
                field.append(c);
                if (field.length() > MAX_FIELD_CHARS) {
                    throw unclosedQuote(quoteLine);
                }
            }
        }

        private IOException unclosedQuote(long quoteLine) {
            return new IOException(file.getFileName() + " has a quoted value starting on line " + quoteLine
                    + " that's never closed.");
        }

        /** Steps past a line break: \n, \r\n or a lone \r. */
        private void endLine(char c) throws IOException {
            position++;
            if (c == '\r' && peek() == '\n') {
                position++;
            }
            line++;
        }

        private int peek() throws IOException {
            if (position == limit && !fill()) {
                return -1;
            }
            return buffer[position];
        }

        private boolean fill() throws IOException {
            int n = reader.read(buffer, 0, buffer.length);
            if (n <= 0) {
                return false;
            }
            position = 0;
            limit = n;
            return true;
        }
    }

    /** The header line (up to the first unquoted line break), without consuming it, for {@link #detectDelimiter}. */
    private static String peekHeader(BufferedReader reader) throws IOException {
        reader.mark(HEADER_SCAN_CHARS + 1);
        StringBuilder header = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < HEADER_SCAN_CHARS; i++) {
            int c = reader.read();
            if (c < 0 || (!quoted && (c == '\n' || c == '\r'))) {
                break;
            }
            if (c == '"') {
                quoted = !quoted;
            }
            header.append((char) c);
        }
        reader.reset();
        return header.toString();
    }

    private static void skipBom(InputStream in) throws IOException {
        in.mark(3);
        byte[] bom = in.readNBytes(3);
        if (!(bom.length == 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF)) {
            in.reset();
        }
    }

    /**
     * True if the whole file is valid UTF-8. Excel's usual export is Windows-1252, and a stray byte can be anywhere
     * in a big file, so this reads it all once up front rather than finding out halfway through an import.
     */
    static boolean isUtf8(Path file) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer bytes = ByteBuffer.allocate(1 << 16);
        CharBuffer chars = CharBuffer.allocate(1 << 16);
        try (InputStream in = Files.newInputStream(file)) {
            while (true) {
                int n = in.read(bytes.array(), bytes.position(), bytes.remaining());
                boolean end = n < 0;
                if (!end) {
                    bytes.position(bytes.position() + n);
                }
                bytes.flip();
                CoderResult result;
                do {
                    chars.clear();
                    result = decoder.decode(bytes, chars, end);
                    if (result.isError()) {
                        return false;
                    }
                } while (result.isOverflow());
                bytes.compact();
                if (end) {
                    chars.clear();
                    return !decoder.flush(chars).isError();
                }
            }
        }
    }

    /** Decodes a whole file's bytes: UTF-8 (skipping a BOM) if valid, otherwise Windows-1252. Used by JsonReader. */
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
            return new String(bytes, start, bytes.length - start, WINDOWS_1252);
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

    private static final class CountingInputStream extends FilterInputStream {
        private volatile long count; // read by the import thread for progress while another thread reads the file
        private long mark;

        CountingInputStream(InputStream in) {
            super(in.markSupported() ? in : new BufferedInputStream(in, 1 << 16));
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }

        @Override
        public synchronized void mark(int readLimit) {
            super.mark(readLimit);
            mark = count;
        }

        @Override
        public synchronized void reset() throws IOException {
            super.reset();
            count = mark;
        }
    }
}

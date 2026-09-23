package com.example.helloapplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class Datasets {

    public record Dataset(Path file, String name, LocalDateTime imported) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Datasets() {
    }

    public static List<Dataset> list() throws IOException {
        return list(DataImporter.DEFAULT_DIRECTORY);
    }

    public static List<Dataset> list(Path directory) throws IOException {
        List<Dataset> datasets = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return datasets;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Datasets::isDatabase).toList()) {
                FileTime modified = Files.getLastModifiedTime(file);
                LocalDateTime when = LocalDateTime.ofInstant(modified.toInstant(), ZoneId.systemDefault());
                datasets.add(new Dataset(file, displayName(file), when));
            }
        }
        datasets.sort(Comparator.comparing(Dataset::imported).reversed()
                .thenComparing(Dataset::name, String.CASE_INSENSITIVE_ORDER));
        return datasets;
    }

    private static boolean isDatabase(Path file) {
        return Files.isRegularFile(file) && isDatabaseName(file.getFileName().toString());
    }

    static boolean isDatabaseName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3");
    }

    static String displayName(Path file) {
        String stem = file.getFileName().toString();
        int dot = stem.lastIndexOf('.');
        if (dot > 0) {
            stem = stem.substring(0, dot);
        }
        if (stem.matches("import-\\d{8}-\\d{6}(-\\d+)?")) {
            LocalDateTime when = LocalDateTime.parse(stem.substring(7, 22), STAMP_FORMAT);
            return "Import of " + when.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH));
        }
        String copy = "";
        if (stem.matches(".*-\\d+")) { // the same files imported again: "customers-2"
            int dash = stem.lastIndexOf('-');
            copy = " (" + stem.substring(dash + 1) + ")";
            stem = stem.substring(0, dash);
        }
        return stem.replaceAll("-and-(\\d+)-more$", ", and $1 more").replace("-", ", ") + copy;
    }
}

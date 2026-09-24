package com.example.helloapplication;

import java.nio.file.Path;

/**
 * Where the app keeps its files (users.db, imported datasets, the sample shop).
 * Run from source ({@code mvnw javafx:run}) it's {@code ./data}, as before. The
 * installed app can't write next to itself (Program Files is read-only), so there
 * it's {@code %LOCALAPPDATA%\HelloApplication\data}, which also keeps the files
 * inside the owner's Windows profile, out of reach of other Windows logins.
 */
final class AppPaths {

    static final Path DATA_DIR = resolveDataDir();

    private AppPaths() {
    }

    private static Path resolveDataDir() {
        String override = System.getProperty("helloapp.data");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        // Set by the launcher jpackage builds, and only by it.
        if (System.getProperty("jpackage.app-path") == null) {
            return Path.of("data");
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData != null && !localAppData.isBlank()
                ? Path.of(localAppData)
                : Path.of(System.getProperty("user.home"), ".helloapplication");
        return base.resolve("HelloApplication").resolve("data");
    }
}

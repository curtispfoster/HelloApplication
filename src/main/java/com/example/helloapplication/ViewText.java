package com.example.helloapplication;

import java.sql.SQLException;

final class ViewText {

    private ViewText() {
    }

    static String plural(long count, String noun) {
        return String.format("%,d %s", count, count == 1 ? noun : noun + "s");
    }

    static String describeCount(int shown, long total) {
        if (total == 0) {
            return "No rows";
        }
        if (shown >= total) {
            return total == 1 ? "1 row" : String.format("All %,d rows", total);
        }
        return String.format("Showing %,d of %,d rows", shown, total);
    }

    static String describeFailure(String action, Throwable error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();

        if (error instanceof IllegalArgumentException) {
            return message; // already written for the user (DatabaseBrowser.checkQuery)
        }
        if (message.contains("NOTADB") || message.contains("not a database")) {
            return "Couldn't " + action + ". It isn't a SQLite database.";
        }
        if (message.contains("BUSY") || message.contains("LOCKED") || message.contains("locked")) {
            return "Couldn't " + action + ". The file may be locked by another program.";
        }
        if (message.contains("CANTOPEN") || message.contains("unable to open")) {
            return "Couldn't " + action + ". Check that the file exists and you can read it.";
        }
        if (message.contains("interrupted") || message.contains("INTERRUPT")) {
            return "Couldn't " + action + ". It took too long and was stopped.";
        }
        if (error instanceof SQLException && message.contains("[SQLITE_ERROR]")) {
            return "Couldn't " + action + ". " + sqliteDetail(message);
        }
        return "Couldn't " + action + ".";
    }

    static String sqliteDetail(String message) {
        int open = message.lastIndexOf('(');
        int close = message.lastIndexOf(')');
        String detail = open >= 0 && close > open ? message.substring(open + 1, close) : message;
        detail = detail.strip();
        if (detail.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(detail.charAt(0)) + detail.substring(1) + (detail.endsWith(".") ? "" : ".");
    }
}

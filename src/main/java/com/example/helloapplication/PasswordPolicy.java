package com.example.helloapplication;

/**
 * Password strength rule for new accounts: at least 8 characters, containing
 * at least one digit and one symbol (any character that isn't a letter or digit).
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static boolean meetsRequirements(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }

        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isLetter(c)) {
                hasSymbol = true;
            }
        }
        return hasDigit && hasSymbol;
    }
}

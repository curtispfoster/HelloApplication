package com.example.helloapplication;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Argon2id password hashing via Bouncy Castle's pure-Java implementation
 * (no native library to load, which keeps this simple on Windows).
 * Encoded form: $argon2id$v=19$m=&lt;memoryKB&gt;,t=&lt;iterations&gt;,p=&lt;parallelism&gt;$&lt;b64 salt&gt;$&lt;b64 hash&gt;
 * Cost parameters are embedded in the stored hash so they can be tuned later
 * without breaking verification of existing hashes.
 */
public final class Argon2PasswordHasher {

    private static final int SALT_LEN = 16;
    private static final int HASH_LEN = 32;
    // OWASP-recommended floor for Argon2id: 19 MiB memory, 2 passes, 1 lane.
    private static final int MEMORY_KB = 19_456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Argon2PasswordHasher() {
    }

    public static String hash(char[] password) {
        byte[] salt = new byte[SALT_LEN];
        RANDOM.nextBytes(salt);
        byte[] hash = rawHash(password, salt, MEMORY_KB, ITERATIONS, PARALLELISM);
        try {
            return encode(salt, hash, MEMORY_KB, ITERATIONS, PARALLELISM);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    public static boolean verify(char[] password, String encoded) {
        Parsed parsed = parse(encoded);
        if (parsed == null) {
            return false;
        }
        byte[] candidate = rawHash(password, parsed.salt, parsed.memoryKb, parsed.iterations, parsed.parallelism);
        try {
            return constantTimeEquals(candidate, parsed.hash);
        } finally {
            Arrays.fill(candidate, (byte) 0);
            Arrays.fill(parsed.hash, (byte) 0);
        }
    }

    private static byte[] rawHash(char[] password, byte[] salt, int memoryKb, int iterations, int parallelism) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKb)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] out = new byte[HASH_LEN];
        generator.generateBytes(password, out);
        return out;
    }

    private static String encode(byte[] salt, byte[] hash, int memoryKb, int iterations, int parallelism) {
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return "$argon2id$v=19$m=" + memoryKb + ",t=" + iterations + ",p=" + parallelism
                + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    private static Parsed parse(String encoded) {
        if (encoded == null) {
            return null;
        }
        String[] parts = encoded.split("\\$");
        // "" , "argon2id", "v=19", "m=..,t=..,p=..", "<salt>", "<hash>"
        if (parts.length != 6 || !"argon2id".equals(parts[1])) {
            return null;
        }
        try {
            String[] cost = parts[3].split(",");
            int memoryKb = Integer.parseInt(cost[0].substring(2));
            int iterations = Integer.parseInt(cost[1].substring(2));
            int parallelism = Integer.parseInt(cost[2].substring(2));
            byte[] salt = Base64.getDecoder().decode(parts[4]);
            byte[] hash = Base64.getDecoder().decode(parts[5]);
            return new Parsed(memoryKb, iterations, parallelism, salt, hash);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static final class Parsed {
        final int memoryKb;
        final int iterations;
        final int parallelism;
        final byte[] salt;
        final byte[] hash;

        Parsed(int memoryKb, int iterations, int parallelism, byte[] salt, byte[] hash) {
            this.memoryKb = memoryKb;
            this.iterations = iterations;
            this.parallelism = parallelism;
            this.salt = salt;
            this.hash = hash;
        }
    }
}

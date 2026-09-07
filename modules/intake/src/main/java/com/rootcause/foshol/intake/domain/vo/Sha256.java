package com.rootcause.foshol.intake.domain.vo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

public record Sha256(String hex) {

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");

    public Sha256 {
        Objects.requireNonNull(hex, "sha256");
        if (!HEX_64.matcher(hex).matches()) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
    }

    public static Sha256 ofBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new Sha256(hexDigest(bytes));
    }

    public static Sha256 ofUtf8(String text) {
        Objects.requireNonNull(text, "text");
        return ofBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    static String hexDigest(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    @Override
    public String toString() {
        return hex;
    }
}

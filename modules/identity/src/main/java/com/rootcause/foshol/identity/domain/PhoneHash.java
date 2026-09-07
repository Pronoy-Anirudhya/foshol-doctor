package com.rootcause.foshol.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PhoneHash {

    private final String hex;

    private PhoneHash(String hex) {
        this.hex = hex;
    }

    public static PhoneHash of(PhoneNumber phone) {
        return new PhoneHash(sha256Hex(phone.e164()));
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String hex() {
        return hex;
    }
}

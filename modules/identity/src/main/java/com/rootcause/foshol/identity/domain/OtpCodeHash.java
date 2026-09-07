package com.rootcause.foshol.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public final class OtpCodeHash {

    private OtpCodeHash() {}

    public static String compute(UUID challengeId, String phoneHash, String code) {
        return PhoneHash.sha256Hex(challengeId.toString() + phoneHash + code);
    }

    public static boolean equalsConstantTime(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}

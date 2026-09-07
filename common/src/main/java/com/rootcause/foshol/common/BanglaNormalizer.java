package com.rootcause.foshol.common;

import java.text.Normalizer;

public final class BanglaNormalizer {

    private BanglaNormalizer() {}

    public static String forStorage(String raw) {
        if (raw == null) {
            return null;
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFC);
    }

    public static String forMatching(String raw) {
        if (raw == null) {
            return null;
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        String stripped = nfc.replace("\u200c", "").replace("\u200d", "");
        return foldBengaliDigits(stripped);
    }

    static String foldBengaliDigits(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '\u09E6' && c <= '\u09EF') {
                out.append((char) ('0' + (c - '\u09E6')));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}

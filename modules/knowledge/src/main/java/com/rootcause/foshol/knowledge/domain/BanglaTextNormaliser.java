package com.rootcause.foshol.knowledge.domain;

import java.text.Normalizer;
import java.util.Locale;

public final class BanglaTextNormaliser {

    private BanglaTextNormaliser() {}

    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(nfc.length());
        for (int i = 0; i < nfc.length(); ) {
            int cp = nfc.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0x200C || cp == 0x200D) {
                continue;
            }
            if (cp >= 0x09E6 && cp <= 0x09EF) {
                out.append((char) ('0' + (cp - 0x09E6)));
                continue;
            }
            if (isPunctuationOrSymbol(cp)) {
                out.append(' ');
                continue;
            }
            out.appendCodePoint(cp);
        }
        String lowered = out.toString().toLowerCase(Locale.ROOT);
        return collapseWhitespace(lowered);
    }

    private static boolean isPunctuationOrSymbol(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL;
    }

    private static String collapseWhitespace(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inSpace = false;
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                if (!inSpace) {
                    out.append(' ');
                    inSpace = true;
                }
            } else {
                out.appendCodePoint(cp);
                inSpace = false;
            }
        }
        int start = 0;
        int end = out.length();
        while (start < end && out.charAt(start) == ' ') {
            start++;
        }
        while (end > start && out.charAt(end - 1) == ' ') {
            end--;
        }
        return out.substring(start, end);
    }
}

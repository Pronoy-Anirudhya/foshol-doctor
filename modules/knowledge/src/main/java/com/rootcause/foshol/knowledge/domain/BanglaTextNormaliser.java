package com.rootcause.foshol.knowledge.domain;

import com.rootcause.foshol.common.util.BanglaNormalizer;
import java.util.Locale;

/**
 * Phrase-matching normaliser. Storage NFC is {@link BanglaNormalizer#forStorage};
 * this additionally maps punctuation to spaces and lowercases, which the matcher needs.
 */
public final class BanglaTextNormaliser {

    private BanglaTextNormaliser() {}

    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String prepared = BanglaNormalizer.forMatching(raw);
        StringBuilder out = new StringBuilder(prepared.length());
        for (int i = 0; i < prepared.length(); ) {
            int cp = prepared.codePointAt(i);
            i += Character.charCount(cp);
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

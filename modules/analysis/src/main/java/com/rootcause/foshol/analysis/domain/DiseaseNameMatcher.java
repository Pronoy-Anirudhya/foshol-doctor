package com.rootcause.foshol.analysis.domain;

import com.rootcause.foshol.common.util.BanglaNormalizer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DiseaseNameMatcher {

    private static final BigDecimal TOKEN_SIMILARITY_MIN = new BigDecimal("0.75");
    private static final BigDecimal JACCARD_MIN = new BigDecimal("0.55");
    private static final int PREFIX_MIN = 2;
    private static final int HASANTA = 0x09CD;

    private DiseaseNameMatcher() {}

    public static List<DiseaseNameHit> match(String transcriptBn, List<DiseaseNameRef> diseases) {
        Set<String> transcriptTokens = tokens(normalise(transcriptBn));
        String normalisedTranscript = normalise(transcriptBn);
        List<DiseaseNameHit> hits = new ArrayList<>();
        if (diseases == null || transcriptTokens.isEmpty()) {
            return List.of();
        }
        for (DiseaseNameRef disease : diseases) {
            if (disease == null || disease.healthy()) {
                continue;
            }
            BigDecimal score = score(disease, normalisedTranscript, transcriptTokens);
            if (score.compareTo(VoiceKbMatchers.NAME_OVERLAP_MIN) >= 0) {
                hits.add(new DiseaseNameHit(
                        disease.id(),
                        disease.code(),
                        disease.nameBn(),
                        disease.nameEn(),
                        AnalysisScale.score(score)));
            }
        }
        return List.copyOf(hits);
    }

    static BigDecimal score(DiseaseNameRef disease, String normalisedTranscript, Set<String> transcriptTokens) {
        BigDecimal best = BigDecimal.ZERO;
        best = max(best, overlap(tokens(normalise(disease.code())), transcriptTokens));
        best = max(best, overlap(tokens(normalise(disease.nameBn())), transcriptTokens));
        best = max(best, overlap(tokens(normalise(disease.nameEn())), transcriptTokens));
        best = max(best, contained(normalise(disease.code()), normalisedTranscript));
        best = max(best, contained(normalise(disease.nameBn()), normalisedTranscript));
        best = max(best, contained(normalise(disease.nameEn()), normalisedTranscript));
        return best;
    }

    static BigDecimal overlap(Set<String> phraseTokens, Set<String> transcriptTokens) {
        if (phraseTokens.isEmpty() || transcriptTokens.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int hits = 0;
        for (String token : phraseTokens) {
            if (fuzzyHit(token, transcriptTokens)) {
                hits++;
            }
        }
        BigDecimal coverage =
                BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(phraseTokens.size()), 10, AnalysisScale.ROUNDING);
        int union = phraseTokens.size() + transcriptTokens.size() - hits;
        if (union <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal jaccard =
                BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(union), 10, AnalysisScale.ROUNDING);
        if (jaccard.compareTo(JACCARD_MIN) < 0) {
            return BigDecimal.ZERO;
        }
        return coverage;
    }

    static BigDecimal contained(String phrase, String transcript) {
        if (phrase == null || phrase.length() < 2 || transcript == null || transcript.isBlank()) {
            return BigDecimal.ZERO;
        }
        return transcript.contains(phrase) ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    static boolean fuzzyHit(String phraseToken, Set<String> transcriptTokens) {
        for (String transcriptToken : transcriptTokens) {
            if (tokensMatch(phraseToken, transcriptToken)) {
                return true;
            }
        }
        return false;
    }

    static boolean tokensMatch(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        if (tokensMatchCore(left, right)) {
            return true;
        }
        String strippedLeft = stripLeadingHa(left);
        String strippedRight = stripLeadingHa(right);
        return tokensMatchCore(strippedLeft, right)
                || tokensMatchCore(left, strippedRight)
                || tokensMatchCore(strippedLeft, strippedRight);
    }

    private static boolean tokensMatchCore(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        if (left.length() >= PREFIX_MIN && right.length() >= PREFIX_MIN
                && (left.startsWith(right) || right.startsWith(left) || left.endsWith(right) || right.endsWith(left))) {
            return true;
        }
        return similarity(left, right).compareTo(TOKEN_SIMILARITY_MIN) >= 0;
    }

    private static String stripLeadingHa(String token) {
        if (token.isEmpty() || token.codePointAt(0) != 0x09B9) {
            return token;
        }
        int i = Character.charCount(token.codePointAt(0));
        if (i < token.length()) {
            int next = token.codePointAt(i);
            if (isBengaliVowelSign(next)) {
                i += Character.charCount(next);
            }
        }
        return i >= token.length() ? token : token.substring(i);
    }

    private static boolean isBengaliVowelSign(int cp) {
        return (cp >= 0x09BE && cp <= 0x09CC) || (cp >= 0x0981 && cp <= 0x0983);
    }

    static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String folded = foldAsr(BanglaNormalizer.forMatching(raw)).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(folded.length());
        for (int i = 0; i < folded.length(); ) {
            int cp = folded.codePointAt(i);
            i += Character.charCount(cp);
            if (isPunctuationOrSymbol(cp) || Character.isWhitespace(cp)) {
                out.append(' ');
            } else {
                out.appendCodePoint(cp);
            }
        }
        return collapse(out.toString());
    }

    static Set<String> tokens(String normalised) {
        if (normalised == null || normalised.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : normalised.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    static String foldAsr(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == HASANTA) {
                if (i < input.length()) {
                    int next = input.codePointAt(i);
                    if (isBengaliConsonant(next)) {
                        i += Character.charCount(next);
                    }
                }
                continue;
            }
            if (cp == 0x09BC) {
                continue;
            }
            out.appendCodePoint(foldLetter(cp));
        }
        return out.toString();
    }

    private static int foldLetter(int cp) {
        if (cp == 0x09B6 || cp == 0x09B7) {
            return 0x09B8;
        }
        if (cp == 0x09A3) {
            return 0x09A8;
        }
        if (cp == 0x09DC) {
            return 0x09A1;
        }
        if (cp == 0x09DD) {
            return 0x09A2;
        }
        if (cp == 0x09DF) {
            return 0x09AF;
        }
        return cp;
    }

    private static boolean isBengaliConsonant(int cp) {
        return (cp >= 0x0995 && cp <= 0x09B9) || (cp >= 0x09DC && cp <= 0x09DF);
    }

    static BigDecimal similarity(String left, String right) {
        int max = Math.max(left.length(), right.length());
        if (max == 0) {
            return BigDecimal.ONE;
        }
        int distance = levenshtein(left, right);
        return BigDecimal.valueOf(max - distance)
                .divide(BigDecimal.valueOf(max), 10, AnalysisScale.ROUNDING);
    }

    private static int levenshtein(String left, String right) {
        int n = left.length();
        int m = right.length();
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            char a = left.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = a == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
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

    private static String collapse(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean space = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ') {
                if (!space) {
                    out.append(' ');
                    space = true;
                }
            } else {
                out.append(c);
                space = false;
            }
        }
        return out.toString().trim();
    }

    private static BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }
}

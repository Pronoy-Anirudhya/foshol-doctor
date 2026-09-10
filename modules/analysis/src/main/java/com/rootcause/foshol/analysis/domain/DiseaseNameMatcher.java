package com.rootcause.foshol.analysis.domain;

import com.rootcause.foshol.common.util.BanglaNormalizer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DiseaseNameMatcher {

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
                        disease.id(), disease.code(), disease.nameBn(), AnalysisScale.score(score)));
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
            if (transcriptTokens.contains(token)) {
                hits++;
            }
        }
        return BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(phraseTokens.size()), 10, AnalysisScale.ROUNDING);
    }

    static BigDecimal contained(String phrase, String transcript) {
        if (phrase == null || phrase.length() < 2 || transcript == null || transcript.isBlank()) {
            return BigDecimal.ZERO;
        }
        return transcript.contains(phrase) ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String folded = BanglaNormalizer.forMatching(raw).toLowerCase(Locale.ROOT);
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

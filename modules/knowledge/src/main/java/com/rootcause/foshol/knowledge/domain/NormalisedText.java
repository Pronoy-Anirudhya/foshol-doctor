package com.rootcause.foshol.knowledge.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record NormalisedText(String value, Set<String> tokens) {

    public NormalisedText {
        value = value == null ? "" : value;
        tokens = tokens == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(tokens));
    }

    public static NormalisedText of(String raw) {
        String normalised = BanglaTextNormaliser.normalise(raw);
        Set<String> tokens = new LinkedHashSet<>();
        if (!normalised.isEmpty()) {
            for (String token : normalised.split(" ")) {
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        }
        return new NormalisedText(normalised, tokens);
    }
}

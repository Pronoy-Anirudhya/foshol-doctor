package com.rootcause.foshol.knowledge.domain;

import java.util.UUID;

public record PhraseIndexEntry(UUID phraseId, UUID symptomId, String normalisedBn) {

    public PhraseIndexEntry {
        if (phraseId == null || symptomId == null) {
            throw new IllegalArgumentException("phraseId and symptomId are required");
        }
        normalisedBn = normalisedBn == null ? "" : normalisedBn;
    }
}

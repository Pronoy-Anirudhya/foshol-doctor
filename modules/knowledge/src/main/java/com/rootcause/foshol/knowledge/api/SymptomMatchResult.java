package com.rootcause.foshol.knowledge.api;

import java.util.List;

public record SymptomMatchResult(
        List<MatchedSymptom> symptoms, List<ScoredDisease> diseases, boolean inconclusive) {}

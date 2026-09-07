package com.rootcause.foshol.knowledge.api;

import java.util.List;
import java.util.UUID;

public record SymptomMatchRequest(
        UUID cropId, String transcriptBn, float[] transcriptEmbedding, List<UUID> officerSymptomIds) {}

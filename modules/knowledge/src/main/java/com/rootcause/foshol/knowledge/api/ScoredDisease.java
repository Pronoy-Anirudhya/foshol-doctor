package com.rootcause.foshol.knowledge.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ScoredDisease(UUID diseaseId, String code, String nameBn, BigDecimal score, int rank) {}

package com.rootcause.foshol.knowledge.api;

import java.math.BigDecimal;
import java.util.UUID;

public record MatchedSymptom(
        UUID symptomId, String code, String nameBn, BigDecimal score, String matcher) {}

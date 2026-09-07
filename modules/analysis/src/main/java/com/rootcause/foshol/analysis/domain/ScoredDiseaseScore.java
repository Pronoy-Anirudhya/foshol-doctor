package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record ScoredDiseaseScore(UUID diseaseId, BigDecimal score, String diseaseCode) {}

package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record RankedCandidate(UUID diseaseId, BigDecimal confidence, int rank, String diseaseCode) {}

package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record MappedCandidate(UUID diseaseId, String diseaseCode, BigDecimal confidence) {}

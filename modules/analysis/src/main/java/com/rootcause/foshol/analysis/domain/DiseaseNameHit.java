package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record DiseaseNameHit(UUID diseaseId, String code, String nameBn, BigDecimal score) {}

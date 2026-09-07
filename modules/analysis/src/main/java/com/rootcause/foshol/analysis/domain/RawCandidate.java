package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;

public record RawCandidate(String rawLabel, BigDecimal confidence) {}

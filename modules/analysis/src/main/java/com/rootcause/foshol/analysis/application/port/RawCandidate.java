package com.rootcause.foshol.analysis.application.port;

import java.math.BigDecimal;

public record RawCandidate(String rawLabel, BigDecimal confidence) {}

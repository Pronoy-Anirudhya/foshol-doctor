package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;

public final class VoiceKbMatchers {

    public static final String NAME = "NAME";
    public static final String VECTOR = "VECTOR";
    public static final String FUZZY = "FUZZY";
    public static final int CANDIDATE_LIMIT = 5;
    public static final String LANGUAGE_BN = "bn";
    public static final BigDecimal NAME_OVERLAP_MIN = new BigDecimal("0.60");

    private VoiceKbMatchers() {}
}

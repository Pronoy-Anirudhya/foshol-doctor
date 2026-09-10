package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record VoiceKbCandidate(
        UUID diseaseId, String code, String nameBn, String nameEn, BigDecimal score, String matcher) {}

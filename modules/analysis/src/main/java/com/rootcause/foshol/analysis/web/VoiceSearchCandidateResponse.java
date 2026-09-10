package com.rootcause.foshol.analysis.web;

import java.math.BigDecimal;
import java.util.UUID;

public record VoiceSearchCandidateResponse(
        UUID diseaseId, String code, String nameBn, BigDecimal score, String matcher) {}

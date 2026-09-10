package com.rootcause.foshol.analysis.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record VoiceKbDiseaseCandidate(
        UUID diseaseId, String code, String nameBn, String nameEn, BigDecimal score, String matcher) {}

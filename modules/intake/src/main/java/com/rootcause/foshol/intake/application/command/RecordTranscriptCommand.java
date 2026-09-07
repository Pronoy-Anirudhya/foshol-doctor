package com.rootcause.foshol.intake.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordTranscriptCommand(UUID caseId, String transcriptBn, BigDecimal asrConfidence) {}

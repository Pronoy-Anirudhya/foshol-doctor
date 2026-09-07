package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.common.cqrs.Command;
import java.math.BigDecimal;
import java.util.UUID;

public record RecordTranscriptCommand(UUID caseId, String transcriptBn, BigDecimal asrConfidence) implements Command {}

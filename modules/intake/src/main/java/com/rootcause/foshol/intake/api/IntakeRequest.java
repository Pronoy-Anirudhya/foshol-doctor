package com.rootcause.foshol.intake.api;

import java.util.List;
import java.util.UUID;

public record IntakeRequest(
        UUID farmerId,
        UUID cropId,
        String noteBn,
        UUID parentCaseId,
        List<IntakeImage> images,
        IntakeAudio audio,
        UUID idempotencyKey) {}

package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.intake.api.IntakeAudio;
import com.rootcause.foshol.intake.api.IntakeImage;
import com.rootcause.foshol.intake.api.IntakeRequest;
import java.util.List;
import java.util.UUID;

public record SubmitCaseCommand(
        UUID farmerId,
        UUID cropId,
        String noteBn,
        UUID parentCaseId,
        List<IntakeImage> images,
        IntakeAudio audio,
        UUID idempotencyKey) {

    public static SubmitCaseCommand from(IntakeRequest request) {
        return new SubmitCaseCommand(
                request.farmerId(),
                request.cropId(),
                request.noteBn(),
                request.parentCaseId(),
                request.images(),
                request.audio(),
                request.idempotencyKey());
    }
}

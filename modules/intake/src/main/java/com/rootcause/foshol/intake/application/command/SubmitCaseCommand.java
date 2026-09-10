package com.rootcause.foshol.intake.application.command;

import com.rootcause.foshol.common.enums.CropQuantityUnit;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.cqrs.Command;
import com.rootcause.foshol.intake.api.IntakeAudio;
import com.rootcause.foshol.intake.api.IntakeImage;
import com.rootcause.foshol.intake.api.IntakeRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SubmitCaseCommand(
        UUID farmerId,
        UUID cropId,
        String noteBn,
        UUID parentCaseId,
        BigDecimal fieldArea,
        FieldAreaUnit fieldAreaUnit,
        BigDecimal cropQuantity,
        CropQuantityUnit cropQuantityUnit,
        List<IntakeImage> images,
        IntakeAudio audio,
        UUID idempotencyKey) implements Command {

    public static SubmitCaseCommand from(IntakeRequest request) {
        return new SubmitCaseCommand(
                request.farmerId(),
                request.cropId(),
                request.noteBn(),
                request.parentCaseId(),
                request.fieldArea(),
                request.fieldAreaUnit(),
                request.cropQuantity(),
                request.cropQuantityUnit(),
                request.images(),
                request.audio(),
                request.idempotencyKey());
    }
}

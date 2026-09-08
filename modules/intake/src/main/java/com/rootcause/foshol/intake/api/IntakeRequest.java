package com.rootcause.foshol.intake.api;

import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.FieldAreaUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record IntakeRequest(
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
        UUID idempotencyKey) {}

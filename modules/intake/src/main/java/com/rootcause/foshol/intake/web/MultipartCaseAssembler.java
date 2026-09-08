package com.rootcause.foshol.intake.web;

import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.intake.api.IntakeAudio;
import com.rootcause.foshol.intake.api.IntakeImage;
import com.rootcause.foshol.intake.api.IntakeRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

final class MultipartCaseAssembler {

    private MultipartCaseAssembler() {}

    static IntakeRequest assemble(
            UUID farmerId,
            UUID cropId,
            String noteBn,
            UUID parentCaseId,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            Integer audioDurationMs,
            List<MultipartFile> images,
            MultipartFile audio,
            UUID idempotencyKey)
            throws Exception {
        List<IntakeImage> intakeImages = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (image == null || image.isEmpty()) {
                    continue;
                }
                intakeImages.add(new IntakeImage("", image.getContentType(), image.getBytes()));
            }
        }
        IntakeAudio intakeAudio = null;
        if (audio != null && !audio.isEmpty()) {
            int duration = audioDurationMs == null ? 0 : audioDurationMs;
            intakeAudio = new IntakeAudio("", audio.getContentType(), audio.getBytes(), duration);
        }
        return new IntakeRequest(
                farmerId,
                cropId,
                noteBn,
                parentCaseId,
                fieldArea,
                fieldAreaUnit,
                cropQuantity,
                cropQuantityUnit,
                List.copyOf(intakeImages),
                intakeAudio,
                idempotencyKey);
    }
}

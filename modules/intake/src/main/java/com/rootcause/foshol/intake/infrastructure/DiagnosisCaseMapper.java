package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import com.rootcause.foshol.intake.domain.CaseAudio;
import com.rootcause.foshol.intake.domain.CaseImage;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.vo.AudioId;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.intake.domain.vo.ImageId;
import com.rootcause.foshol.intake.domain.vo.ImageQuality;
import com.rootcause.foshol.intake.domain.vo.ObjectKey;
import com.rootcause.foshol.intake.domain.vo.Sha256;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

final class DiagnosisCaseMapper {

    private DiagnosisCaseMapper() {}

    static DiagnosisCase toDomain(DiagnosisCaseEntity entity) {
        var images = entity.getImages().stream()
                .sorted(Comparator.comparingInt(CaseImageEntity::getPosition))
                .map(DiagnosisCaseMapper::toImage)
                .toList();
        CaseAudio audio = entity.getAudio() == null ? null : toAudio(entity.getAudio());
        return DiagnosisCase.rehydrate(
                CaseId.of(entity.getId()),
                entity.getFarmerId(),
                entity.getCropId(),
                entity.getParentCaseId() == null ? null : CaseId.of(entity.getParentCaseId()),
                CaseStatus.valueOf(entity.getStatus()),
                entity.getDecisionPath() == null ? null : DecisionPath.valueOf(entity.getDecisionPath()),
                entity.getNoteBn(),
                entity.getDistrictCode(),
                entity.getDivisionCode(),
                entity.getCorrelationId(),
                entity.getFieldArea(),
                FieldAreaUnit.valueOf(entity.getFieldAreaUnit()),
                entity.getCropQuantity(),
                entity.getCropQuantityUnit() == null
                        ? null
                        : CropQuantityUnit.valueOf(entity.getCropQuantityUnit()),
                MetricsSource.valueOf(entity.getMetricsSource()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                images,
                audio);
    }

    static void copyInto(DiagnosisCase domain, DiagnosisCaseEntity entity) {
        entity.setStatus(domain.status().name());
        entity.setDecisionPath(domain.decisionPath() == null ? null : domain.decisionPath().name());
        entity.setFieldArea(domain.fieldArea());
        entity.setFieldAreaUnit(domain.fieldAreaUnit().name());
        entity.setCropQuantity(domain.cropQuantity());
        entity.setCropQuantityUnit(domain.cropQuantityUnit() == null ? null : domain.cropQuantityUnit().name());
        entity.setMetricsSource(domain.metricsSource().name());
        entity.setUpdatedAt(domain.updatedAt());
        if (domain.audio() != null && entity.getAudio() != null) {
            entity.getAudio().setTranscriptBn(domain.audio().transcriptBn());
            entity.getAudio().setAsrConfidence(domain.audio().asrConfidence());
        }
    }

    static DiagnosisCaseEntity toNewEntity(DiagnosisCase domain) {
        DiagnosisCaseEntity entity = new DiagnosisCaseEntity(
                domain.id().value(),
                domain.farmerId(),
                domain.cropId(),
                domain.parentCaseId() == null ? null : domain.parentCaseId().value(),
                domain.status().name(),
                domain.decisionPath() == null ? null : domain.decisionPath().name(),
                domain.noteBn(),
                domain.districtCode(),
                domain.divisionCode(),
                domain.correlationId(),
                domain.fieldArea(),
                domain.fieldAreaUnit().name(),
                domain.cropQuantity(),
                domain.cropQuantityUnit() == null ? null : domain.cropQuantityUnit().name(),
                domain.metricsSource().name(),
                domain.version(),
                domain.createdAt(),
                domain.updatedAt());
        for (CaseImage image : domain.images()) {
            entity.addImage(toImageEntity(image, domain.createdAt()));
        }
        if (domain.audio() != null) {
            entity.setAudio(toAudioEntity(domain.audio(), domain.createdAt()));
        }
        return entity;
    }

    private static CaseImage toImage(CaseImageEntity entity) {
        return new CaseImage(
                ImageId.of(entity.getId()),
                new ObjectKey(entity.getObjectKey()),
                entity.getDerivativeObjectKey() == null ? null : new ObjectKey(entity.getDerivativeObjectKey()),
                entity.getContentType(),
                entity.getByteSize(),
                new ImageQuality(
                        entity.getBlurVariance() == null ? 0 : entity.getBlurVariance().doubleValue(),
                        entity.getExposureScore() == null ? 0 : entity.getExposureScore().doubleValue(),
                        entity.getQualityScore() == null ? BigDecimal.ZERO : entity.getQualityScore(),
                        entity.getWidth() == null ? 0 : entity.getWidth(),
                        entity.getHeight() == null ? 0 : entity.getHeight()),
                new Sha256(entity.getSha256()),
                entity.isPrimary(),
                entity.getPosition());
    }

    private static CaseImageEntity toImageEntity(CaseImage image, java.time.Instant createdAt) {
        return new CaseImageEntity(
                image.id().value(),
                image.objectKey().value(),
                image.derivativeObjectKey() == null ? null : image.derivativeObjectKey().value(),
                image.contentType(),
                image.byteSize(),
                image.quality().width(),
                image.quality().height(),
                image.sha256().hex(),
                image.quality().qualityScore(),
                BigDecimal.valueOf(image.quality().blurVariance()).setScale(3, RoundingMode.HALF_UP),
                BigDecimal.valueOf(image.quality().exposureScore()).setScale(3, RoundingMode.HALF_UP),
                image.primary(),
                (short) image.position(),
                createdAt);
    }

    private static CaseAudio toAudio(CaseAudioEntity entity) {
        return new CaseAudio(
                AudioId.of(entity.getId()),
                new ObjectKey(entity.getObjectKey()),
                entity.getDurationMs(),
                entity.getSampleRateHz(),
                entity.getByteSize(),
                entity.getTranscriptBn(),
                entity.getAsrConfidence());
    }

    private static CaseAudioEntity toAudioEntity(CaseAudio audio, java.time.Instant createdAt) {
        CaseAudioEntity entity = new CaseAudioEntity(
                audio.id().value(),
                audio.objectKey().value(),
                audio.durationMs(),
                audio.sampleRateHz(),
                audio.byteSize(),
                createdAt);
        entity.setTranscriptBn(audio.transcriptBn());
        entity.setAsrConfidence(audio.asrConfidence());
        return entity;
    }
}

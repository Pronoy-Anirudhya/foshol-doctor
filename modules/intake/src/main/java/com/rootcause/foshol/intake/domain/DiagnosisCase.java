package com.rootcause.foshol.intake.domain;

import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.enums.CropQuantityUnit;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.enums.MetricsSource;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.intake.domain.spec.TransitionAllowedSpec;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.intake.domain.vo.ImageId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class DiagnosisCase {

    private final CaseId id;
    private final UUID farmerId;
    private final UUID cropId;
    private final CaseId parentCaseId;
    private CaseStatus status;
    private DecisionPath decisionPath;
    private final String noteBn;
    private final String districtCode;
    private final String divisionCode;
    private final String correlationId;
    private BigDecimal fieldArea;
    private FieldAreaUnit fieldAreaUnit;
    private BigDecimal cropQuantity;
    private CropQuantityUnit cropQuantityUnit;
    private MetricsSource metricsSource;
    private int version;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<CaseImage> images;
    private CaseAudio audio;

    private DiagnosisCase(
            CaseId id,
            UUID farmerId,
            UUID cropId,
            CaseId parentCaseId,
            CaseStatus status,
            DecisionPath decisionPath,
            String noteBn,
            String districtCode,
            String divisionCode,
            String correlationId,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            MetricsSource metricsSource,
            int version,
            Instant createdAt,
            Instant updatedAt,
            List<CaseImage> images,
            CaseAudio audio) {
        this.id = Objects.requireNonNull(id, "id");
        this.farmerId = Objects.requireNonNull(farmerId, "farmerId");
        this.cropId = Objects.requireNonNull(cropId, "cropId");
        this.parentCaseId = parentCaseId;
        this.status = Objects.requireNonNull(status, "status");
        this.decisionPath = decisionPath;
        this.noteBn = noteBn;
        this.districtCode = Objects.requireNonNull(districtCode, "districtCode");
        this.divisionCode = Objects.requireNonNull(divisionCode, "divisionCode");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.fieldArea = Objects.requireNonNull(fieldArea, "fieldArea");
        this.fieldAreaUnit = Objects.requireNonNull(fieldAreaUnit, "fieldAreaUnit");
        this.cropQuantity = cropQuantity;
        this.cropQuantityUnit = cropQuantityUnit;
        this.metricsSource = Objects.requireNonNull(metricsSource, "metricsSource");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.images = new ArrayList<>(Objects.requireNonNull(images, "images"));
        this.audio = audio;
        assertInvariants();
    }

    public static DiagnosisCase submit(
            CaseId id,
            UUID farmerId,
            UUID cropId,
            CaseId parentCaseId,
            String noteBn,
            String districtCode,
            String divisionCode,
            String correlationId,
            Instant submittedAt,
            List<CaseImage> images,
            CaseAudio audio,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            int minImages,
            int maxImages) {
        DiagnosisCase diagnosisCase = new DiagnosisCase(
                id,
                farmerId,
                cropId,
                parentCaseId,
                CaseStatus.SUBMITTED,
                null,
                noteBn,
                districtCode,
                divisionCode,
                correlationId,
                fieldArea,
                fieldAreaUnit,
                cropQuantity,
                cropQuantityUnit,
                MetricsSource.FORM,
                0,
                submittedAt,
                submittedAt,
                markPrimary(images),
                audio);
        diagnosisCase.assertImageCount(minImages, maxImages);
        return diagnosisCase;
    }

    public static DiagnosisCase rehydrate(
            CaseId id,
            UUID farmerId,
            UUID cropId,
            CaseId parentCaseId,
            CaseStatus status,
            DecisionPath decisionPath,
            String noteBn,
            String districtCode,
            String divisionCode,
            String correlationId,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            MetricsSource metricsSource,
            int version,
            Instant createdAt,
            Instant updatedAt,
            List<CaseImage> images,
            CaseAudio audio) {
        return new DiagnosisCase(
                id,
                farmerId,
                cropId,
                parentCaseId,
                status,
                decisionPath,
                noteBn,
                districtCode,
                divisionCode,
                correlationId,
                fieldArea,
                fieldAreaUnit,
                cropQuantity,
                cropQuantityUnit,
                metricsSource,
                version,
                createdAt,
                updatedAt,
                images,
                audio);
    }

    public CaseStatusChanged transitionTo(CaseStatus target, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        if (status == target) {
            return null;
        }
        if (!TransitionAllowedSpec.INSTANCE.isSatisfied(status, target)) {
            throw new IllegalCaseTransitionException(id, status, target, correlationId);
        }
        CaseStatus from = status;
        status = target;
        updatedAt = now;
        return new CaseStatusChanged(id.value(), farmerId, from, target, correlationId, now);
    }

    public void recordDecisionPath(DecisionPath path) {
        Objects.requireNonNull(path, "path");
        if (decisionPath == null) {
            decisionPath = path;
        }
    }

    public void recordTranscript(String transcriptBn, BigDecimal asrConfidence) {
        if (audio == null) {
            throw new AudioNotFoundException(id);
        }
        audio = audio.withTranscript(transcriptBn, asrConfidence);
    }

    public void recordFieldMetrics(
            BigDecimal incomingArea,
            FieldAreaUnit incomingAreaUnit,
            BigDecimal incomingQuantity,
            CropQuantityUnit incomingQuantityUnit,
            MetricsSource source) {
        Objects.requireNonNull(source, "source");
        if (source == MetricsSource.FORM) {
            Objects.requireNonNull(incomingArea, "fieldArea");
            Objects.requireNonNull(incomingAreaUnit, "fieldAreaUnit");
            fieldArea = incomingArea;
            fieldAreaUnit = incomingAreaUnit;
            if (incomingQuantity != null) {
                cropQuantity = incomingQuantity;
                cropQuantityUnit = Objects.requireNonNull(incomingQuantityUnit, "cropQuantityUnit");
            }
            metricsSource = metricsSource == MetricsSource.SPEECH || metricsSource == MetricsSource.FORM_AND_SPEECH
                    ? MetricsSource.FORM_AND_SPEECH
                    : MetricsSource.FORM;
            return;
        }
        boolean formOwnsArea = metricsSource == MetricsSource.FORM
                || metricsSource == MetricsSource.FORM_AND_SPEECH;
        if (!formOwnsArea) {
            if (incomingArea != null && incomingAreaUnit != null) {
                fieldArea = incomingArea;
                fieldAreaUnit = incomingAreaUnit;
            }
            if (incomingQuantity != null) {
                cropQuantity = incomingQuantity;
                cropQuantityUnit = Objects.requireNonNull(incomingQuantityUnit, "cropQuantityUnit");
            }
            metricsSource = MetricsSource.SPEECH;
            return;
        }
        boolean filledQuantity = false;
        boolean differs = false;
        if (incomingArea != null
                && (fieldArea.compareTo(incomingArea) != 0 || fieldAreaUnit != incomingAreaUnit)) {
            differs = true;
        }
        if (cropQuantity == null && incomingQuantity != null) {
            cropQuantity = incomingQuantity;
            cropQuantityUnit = Objects.requireNonNull(incomingQuantityUnit, "cropQuantityUnit");
            filledQuantity = true;
        } else if (incomingQuantity != null
                && cropQuantity != null
                && (cropQuantity.compareTo(incomingQuantity) != 0
                        || cropQuantityUnit != incomingQuantityUnit)) {
            differs = true;
        }
        if (filledQuantity || differs) {
            metricsSource = MetricsSource.FORM_AND_SPEECH;
        }
    }

    public CaseImage image(UUID imageId) {
        return images.stream()
                .filter(image -> image.id().value().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ImageNotFoundException(ImageId.of(imageId)));
    }

    public CaseImage primaryImage() {
        return images.stream()
                .filter(CaseImage::primary)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no primary image"));
    }

    public CaseId id() {
        return id;
    }

    public UUID farmerId() {
        return farmerId;
    }

    public UUID cropId() {
        return cropId;
    }

    public CaseId parentCaseId() {
        return parentCaseId;
    }

    public CaseStatus status() {
        return status;
    }

    public DecisionPath decisionPath() {
        return decisionPath;
    }

    public String noteBn() {
        return noteBn;
    }

    public String districtCode() {
        return districtCode;
    }

    public String divisionCode() {
        return divisionCode;
    }

    public String correlationId() {
        return correlationId;
    }

    public BigDecimal fieldArea() {
        return fieldArea;
    }

    public FieldAreaUnit fieldAreaUnit() {
        return fieldAreaUnit;
    }

    public BigDecimal cropQuantity() {
        return cropQuantity;
    }

    public CropQuantityUnit cropQuantityUnit() {
        return cropQuantityUnit;
    }

    public MetricsSource metricsSource() {
        return metricsSource;
    }

    public int version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<CaseImage> images() {
        return List.copyOf(images);
    }

    public CaseAudio audio() {
        return audio;
    }

    public boolean isTerminal() {
        return status == CaseStatus.ADVISED || status == CaseStatus.REJECTED;
    }

    private void assertImageCount(int minImages, int maxImages) {
        if (images.size() < minImages || images.size() > maxImages) {
            throw new IllegalStateException("image count must be between " + minImages + " and " + maxImages);
        }
    }

    private void assertInvariants() {
        if (districtCode.isBlank()) {
            throw new IllegalStateException("district code is required");
        }
        if (divisionCode.isBlank()) {
            throw new IllegalStateException("division code is required");
        }
        if (correlationId.isBlank()) {
            throw new IllegalStateException("correlation id is required");
        }
        if (fieldArea.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("field area must be positive");
        }
        if (cropQuantity != null && cropQuantityUnit == null) {
            throw new IllegalStateException("crop quantity unit is required when quantity is set");
        }
        if (cropQuantity == null && cropQuantityUnit != null) {
            throw new IllegalStateException("crop quantity unit requires a quantity");
        }
        if (images.isEmpty()) {
            throw new IllegalStateException("a case must carry at least one image");
        }
        long primaries = images.stream().filter(CaseImage::primary).count();
        if (primaries != 1) {
            throw new IllegalStateException("exactly one image must be primary");
        }
        List<Integer> positions = images.stream().map(CaseImage::position).sorted().toList();
        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i) != i + 1) {
                throw new IllegalStateException("image positions must be contiguous from 1");
            }
        }
        for (CaseImage image : images) {
            if (image.sha256() == null || image.objectKey() == null) {
                throw new IllegalStateException("every image needs a sha256 and object key");
            }
        }
        if (decisionPath != null && status == CaseStatus.SUBMITTED) {
            throw new IllegalStateException("decision path must be null until analysis completes");
        }
    }

    static List<CaseImage> markPrimary(List<CaseImage> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        CaseImage winner = incoming.stream()
                .max(Comparator.comparing((CaseImage image) -> image.quality().qualityScore())
                        .thenComparing(Comparator.comparingInt(CaseImage::position).reversed()))
                .orElseThrow();
        List<CaseImage> marked = new ArrayList<>(incoming.size());
        for (CaseImage image : incoming) {
            marked.add(image.id().equals(winner.id()) ? image.asPrimary() : image.asSecondary());
        }
        return marked;
    }
}

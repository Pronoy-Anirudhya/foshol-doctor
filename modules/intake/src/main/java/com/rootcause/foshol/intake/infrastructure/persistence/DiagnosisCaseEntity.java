package com.rootcause.foshol.intake.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "diagnosis_case")
public class DiagnosisCaseEntity {

    @Id
    private UUID id;

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "crop_id", nullable = false)
    private UUID cropId;

    @Column(name = "parent_case_id")
    private UUID parentCaseId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "decision_path", length = 16)
    private String decisionPath;

    @Column(name = "note_bn")
    private String noteBn;

    @Column(name = "district_code", nullable = false, length = 16)
    private String districtCode;

    @Column(name = "division_code", nullable = false, length = 8)
    private String divisionCode;

    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    @Column(name = "field_area", nullable = false, precision = 12, scale = 3)
    private BigDecimal fieldArea;

    @Column(name = "field_area_unit", nullable = false, length = 16)
    private String fieldAreaUnit;

    @Column(name = "crop_quantity", precision = 12, scale = 3)
    private BigDecimal cropQuantity;

    @Column(name = "crop_quantity_unit", length = 16)
    private String cropQuantityUnit;

    @Column(name = "metrics_source", nullable = false, length = 24)
    private String metricsSource;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "diagnosisCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseImageEntity> images = new ArrayList<>();

    @OneToOne(mappedBy = "diagnosisCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private CaseAudioEntity audio;

    protected DiagnosisCaseEntity() {}

    public DiagnosisCaseEntity(
            UUID id,
            UUID farmerId,
            UUID cropId,
            UUID parentCaseId,
            String status,
            String decisionPath,
            String noteBn,
            String districtCode,
            String divisionCode,
            String correlationId,
            BigDecimal fieldArea,
            String fieldAreaUnit,
            BigDecimal cropQuantity,
            String cropQuantityUnit,
            String metricsSource,
            int version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.farmerId = farmerId;
        this.cropId = cropId;
        this.parentCaseId = parentCaseId;
        this.status = status;
        this.decisionPath = decisionPath;
        this.noteBn = noteBn;
        this.districtCode = districtCode;
        this.divisionCode = divisionCode;
        this.correlationId = correlationId;
        this.fieldArea = fieldArea;
        this.fieldAreaUnit = fieldAreaUnit;
        this.cropQuantity = cropQuantity;
        this.cropQuantityUnit = cropQuantityUnit;
        this.metricsSource = metricsSource;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFarmerId() {
        return farmerId;
    }

    public UUID getCropId() {
        return cropId;
    }

    public UUID getParentCaseId() {
        return parentCaseId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDecisionPath() {
        return decisionPath;
    }

    public void setDecisionPath(String decisionPath) {
        this.decisionPath = decisionPath;
    }

    public String getNoteBn() {
        return noteBn;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public String getDivisionCode() {
        return divisionCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public BigDecimal getFieldArea() {
        return fieldArea;
    }

    public void setFieldArea(BigDecimal fieldArea) {
        this.fieldArea = fieldArea;
    }

    public String getFieldAreaUnit() {
        return fieldAreaUnit;
    }

    public void setFieldAreaUnit(String fieldAreaUnit) {
        this.fieldAreaUnit = fieldAreaUnit;
    }

    public BigDecimal getCropQuantity() {
        return cropQuantity;
    }

    public void setCropQuantity(BigDecimal cropQuantity) {
        this.cropQuantity = cropQuantity;
    }

    public String getCropQuantityUnit() {
        return cropQuantityUnit;
    }

    public void setCropQuantityUnit(String cropQuantityUnit) {
        this.cropQuantityUnit = cropQuantityUnit;
    }

    public String getMetricsSource() {
        return metricsSource;
    }

    public void setMetricsSource(String metricsSource) {
        this.metricsSource = metricsSource;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<CaseImageEntity> getImages() {
        return images;
    }

    public CaseAudioEntity getAudio() {
        return audio;
    }

    public void setAudio(CaseAudioEntity audio) {
        this.audio = audio;
        if (audio != null) {
            audio.setDiagnosisCase(this);
        }
    }

    public void addImage(CaseImageEntity image) {
        images.add(image);
        image.setDiagnosisCase(this);
    }
}

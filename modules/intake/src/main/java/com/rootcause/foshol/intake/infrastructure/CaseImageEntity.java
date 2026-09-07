package com.rootcause.foshol.intake.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "case_image")
public class CaseImageEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private DiagnosisCaseEntity diagnosisCase;

    @Column(name = "object_key", nullable = false, length = 200)
    private String objectKey;

    @Column(name = "derivative_object_key", length = 200)
    private String derivativeObjectKey;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "quality_score")
    private BigDecimal qualityScore;

    @Column(name = "blur_variance")
    private BigDecimal blurVariance;

    @Column(name = "exposure_score")
    private BigDecimal exposureScore;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false)
    private short position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseImageEntity() {}

    public CaseImageEntity(
            UUID id,
            String objectKey,
            String derivativeObjectKey,
            String contentType,
            int byteSize,
            Integer width,
            Integer height,
            String sha256,
            BigDecimal qualityScore,
            BigDecimal blurVariance,
            BigDecimal exposureScore,
            boolean primary,
            short position,
            Instant createdAt) {
        this.id = id;
        this.objectKey = objectKey;
        this.derivativeObjectKey = derivativeObjectKey;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
        this.sha256 = sha256;
        this.qualityScore = qualityScore;
        this.blurVariance = blurVariance;
        this.exposureScore = exposureScore;
        this.primary = primary;
        this.position = position;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getDerivativeObjectKey() {
        return derivativeObjectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public int getByteSize() {
        return byteSize;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public String getSha256() {
        return sha256;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public BigDecimal getBlurVariance() {
        return blurVariance;
    }

    public BigDecimal getExposureScore() {
        return exposureScore;
    }

    public boolean isPrimary() {
        return primary;
    }

    public short getPosition() {
        return position;
    }

    public void setDiagnosisCase(DiagnosisCaseEntity diagnosisCase) {
        this.diagnosisCase = diagnosisCase;
    }
}

package com.rootcause.foshol.analysis.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_run")
public class AnalysisRunEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false, length = 8)
    private String mode;

    @Column(name = "vision_model_id", length = 160)
    private String visionModelId;

    @Column(name = "vision_model_version", length = 64)
    private String visionModelVersion;

    @Column(name = "asr_model_id", length = 160)
    private String asrModelId;

    @Column(name = "embed_model_id", length = 160)
    private String embedModelId;

    @Column(name = "top1_confidence", precision = 5, scale = 4)
    private BigDecimal top1Confidence;

    @Column(name = "top2_confidence", precision = 5, scale = 4)
    private BigDecimal top2Confidence;

    @Column(precision = 5, scale = 4)
    private BigDecimal margin;

    @Column(name = "decision_path", length = 16)
    private String decisionPath;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "gradcam_object_key", length = 200)
    private String gradcamObjectKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unmapped_labels", nullable = false, columnDefinition = "jsonb")
    private String unmappedLabels = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_output", columnDefinition = "jsonb")
    private String rawOutput;

    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnalysisRunEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getVisionModelId() {
        return visionModelId;
    }

    public void setVisionModelId(String visionModelId) {
        this.visionModelId = visionModelId;
    }

    public String getVisionModelVersion() {
        return visionModelVersion;
    }

    public void setVisionModelVersion(String visionModelVersion) {
        this.visionModelVersion = visionModelVersion;
    }

    public String getAsrModelId() {
        return asrModelId;
    }

    public void setAsrModelId(String asrModelId) {
        this.asrModelId = asrModelId;
    }

    public String getEmbedModelId() {
        return embedModelId;
    }

    public void setEmbedModelId(String embedModelId) {
        this.embedModelId = embedModelId;
    }

    public BigDecimal getTop1Confidence() {
        return top1Confidence;
    }

    public void setTop1Confidence(BigDecimal top1Confidence) {
        this.top1Confidence = top1Confidence;
    }

    public BigDecimal getTop2Confidence() {
        return top2Confidence;
    }

    public void setTop2Confidence(BigDecimal top2Confidence) {
        this.top2Confidence = top2Confidence;
    }

    public BigDecimal getMargin() {
        return margin;
    }

    public void setMargin(BigDecimal margin) {
        this.margin = margin;
    }

    public String getDecisionPath() {
        return decisionPath;
    }

    public void setDecisionPath(String decisionPath) {
        this.decisionPath = decisionPath;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getGradcamObjectKey() {
        return gradcamObjectKey;
    }

    public void setGradcamObjectKey(String gradcamObjectKey) {
        this.gradcamObjectKey = gradcamObjectKey;
    }

    public String getUnmappedLabels() {
        return unmappedLabels;
    }

    public void setUnmappedLabels(String unmappedLabels) {
        this.unmappedLabels = unmappedLabels;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public void setRawOutput(String rawOutput) {
        this.rawOutput = rawOutput;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

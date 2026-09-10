package com.rootcause.foshol.intake.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_audio")
public class CaseAudioEntity {

    @Id
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private DiagnosisCaseEntity diagnosisCase;

    @Column(name = "object_key", nullable = false, length = 200)
    private String objectKey;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "sample_rate_hz", nullable = false)
    private int sampleRateHz;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "transcript_bn")
    private String transcriptBn;

    @Column(name = "asr_confidence")
    private BigDecimal asrConfidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseAudioEntity() {}

    public CaseAudioEntity(
            UUID id, String objectKey, int durationMs, int sampleRateHz, int byteSize, Instant createdAt) {
        this.id = id;
        this.objectKey = objectKey;
        this.durationMs = durationMs;
        this.sampleRateHz = sampleRateHz;
        this.byteSize = byteSize;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public int getSampleRateHz() {
        return sampleRateHz;
    }

    public int getByteSize() {
        return byteSize;
    }

    public String getTranscriptBn() {
        return transcriptBn;
    }

    public void setTranscriptBn(String transcriptBn) {
        this.transcriptBn = transcriptBn;
    }

    public BigDecimal getAsrConfidence() {
        return asrConfidence;
    }

    public void setAsrConfidence(BigDecimal asrConfidence) {
        this.asrConfidence = asrConfidence;
    }

    public void setDiagnosisCase(DiagnosisCaseEntity diagnosisCase) {
        this.diagnosisCase = diagnosisCase;
    }
}

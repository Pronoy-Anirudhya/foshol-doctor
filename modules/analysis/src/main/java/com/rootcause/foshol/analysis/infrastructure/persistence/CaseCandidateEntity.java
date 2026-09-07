package com.rootcause.foshol.analysis.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_candidate")
public class CaseCandidateEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "disease_id", nullable = false)
    private UUID diseaseId;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "\"rank\"", nullable = false)
    private short rank;

    @Column(nullable = false, length = 8)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseCandidateEntity() {}

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

    public UUID getDiseaseId() {
        return diseaseId;
    }

    public void setDiseaseId(UUID diseaseId) {
        this.diseaseId = diseaseId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public short getRank() {
        return rank;
    }

    public void setRank(short rank) {
        this.rank = rank;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

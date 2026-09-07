package com.rootcause.foshol.analysis.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_symptom")
public class CaseSymptomEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "symptom_id", nullable = false)
    private UUID symptomId;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal score;

    @Column(nullable = false, length = 8)
    private String source;

    @Column(length = 8)
    private String matcher;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseSymptomEntity() {}

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

    public UUID getSymptomId() {
        return symptomId;
    }

    public void setSymptomId(UUID symptomId) {
        this.symptomId = symptomId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMatcher() {
        return matcher;
    }

    public void setMatcher(String matcher) {
        this.matcher = matcher;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

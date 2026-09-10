package com.rootcause.foshol.analysis.domain;

import com.rootcause.foshol.common.enums.CandidateSource;
import com.rootcause.foshol.common.util.Uuid7;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public final class CaseCandidate {

    private final UUID id;
    private final UUID caseId;
    private final UUID diseaseId;
    private final BigDecimal confidence;
    private final int rank;
    private final CandidateSource source;

    public CaseCandidate(
            UUID id, UUID caseId, UUID diseaseId, BigDecimal confidence, int rank, CandidateSource source) {
        if (caseId == null || diseaseId == null || confidence == null || source == null) {
            throw new IllegalArgumentException("Case candidate fields are required.");
        }
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confidence must be in [0, 1].");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("Rank must be 1-based.");
        }
        this.id = id == null ? Uuid7.create() : id;
        this.caseId = caseId;
        this.diseaseId = diseaseId;
        this.confidence = confidence.setScale(4, RoundingMode.HALF_UP);
        this.rank = rank;
        this.source = source;
    }

    public UUID id() {
        return id;
    }

    public UUID caseId() {
        return caseId;
    }

    public UUID diseaseId() {
        return diseaseId;
    }

    public BigDecimal confidence() {
        return confidence;
    }

    public int rank() {
        return rank;
    }

    public CandidateSource source() {
        return source;
    }
}

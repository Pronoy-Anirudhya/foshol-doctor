package com.rootcause.foshol.analysis.domain;

import com.rootcause.foshol.common.enums.SymptomSource;
import com.rootcause.foshol.common.util.Uuid7;
import java.math.BigDecimal;
import java.util.UUID;

public final class CaseSymptom {

    public static final String MATCHER_VECTOR = "VECTOR";
    public static final String MATCHER_FUZZY = "FUZZY";
    public static final String MATCHER_MANUAL = "MANUAL";

    private final UUID id;
    private final UUID caseId;
    private final UUID symptomId;
    private final BigDecimal score;
    private final SymptomSource source;
    private final String matcher;

    public CaseSymptom(
            UUID id, UUID caseId, UUID symptomId, BigDecimal score, SymptomSource source, String matcher) {
        if (caseId == null || symptomId == null || score == null || source == null) {
            throw new AnalysisInvariantException("Case symptom fields are required");
        }
        if (source == SymptomSource.VISION) {
            throw new AnalysisInvariantException("INV-S1: VISION symptoms are not written");
        }
        if (score.compareTo(BigDecimal.ZERO) <= 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new AnalysisInvariantException("INV-S3: score must be in (0, 1]");
        }
        if (source == SymptomSource.OFFICER && !MATCHER_MANUAL.equals(matcher)) {
            throw new AnalysisInvariantException("INV-S2: officer matcher must be MANUAL");
        }
        if (source == SymptomSource.SPEECH
                && !MATCHER_VECTOR.equals(matcher)
                && !MATCHER_FUZZY.equals(matcher)) {
            throw new AnalysisInvariantException("INV-S2: speech matcher must be VECTOR or FUZZY");
        }
        this.id = id == null ? Uuid7.create() : id;
        this.caseId = caseId;
        this.symptomId = symptomId;
        this.score = AnalysisScale.score(score);
        this.source = source;
        this.matcher = matcher;
    }

    public static CaseSymptom officer(UUID caseId, UUID symptomId) {
        return new CaseSymptom(null, caseId, symptomId, BigDecimal.ONE, SymptomSource.OFFICER, MATCHER_MANUAL);
    }

    public UUID id() {
        return id;
    }

    public UUID caseId() {
        return caseId;
    }

    public UUID symptomId() {
        return symptomId;
    }

    public BigDecimal score() {
        return score;
    }

    public SymptomSource source() {
        return source;
    }

    public String matcher() {
        return matcher;
    }
}

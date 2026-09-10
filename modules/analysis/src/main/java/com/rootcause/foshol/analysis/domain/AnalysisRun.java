package com.rootcause.foshol.analysis.domain;

import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.SymptomSource;
import com.rootcause.foshol.common.util.Uuid7;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AnalysisRun {

    private final UUID id;
    private final UUID caseId;
    private final AiMode mode;
    private String visionModelId;
    private String visionModelVersion;
    private String asrModelId;
    private String embedModelId;
    private BigDecimal top1Confidence;
    private BigDecimal top2Confidence;
    private BigDecimal margin;
    private DecisionPath decisionPath;
    private int latencyMs;
    private String gradcamObjectKey;
    private List<String> unmappedLabels = List.of();
    private String rawOutput;
    private String errorCode;
    private final Instant createdAt;
    private boolean completed;
    private final List<CaseCandidate> candidates = new ArrayList<>();
    private final List<CaseSymptom> symptoms = new ArrayList<>();

    public AnalysisRun(UUID id, UUID caseId, AiMode mode, Instant createdAt) {
        if (caseId == null || mode == null) {
            throw new AnalysisInvariantException("INV-A1: caseId and mode are required");
        }
        this.id = id == null ? Uuid7.create() : id;
        this.caseId = caseId;
        this.mode = mode;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public void complete(
            DecisionPath decisionPath,
            BigDecimal top1,
            BigDecimal top2,
            int latencyMs,
            String errorCode,
            List<String> unmappedLabels,
            String rawOutput,
            String visionModelId,
            String visionModelVersion,
            String asrModelId,
            String embedModelId,
            String gradcamObjectKey) {
        if (completed) {
            throw new AnalysisInvariantException("INV-A9: completed run is terminal");
        }
        if (decisionPath == null) {
            throw new AnalysisInvariantException("INV-A2: decisionPath is required on completion");
        }
        this.decisionPath = decisionPath;
        this.top1Confidence = top1 == null ? null : AnalysisScale.confidence(top1);
        if (top2 == null) {
            this.top2Confidence = null;
            this.margin = null;
        } else {
            this.top2Confidence = AnalysisScale.confidence(top2);
            this.margin = this.top1Confidence == null
                    ? null
                    : AnalysisScale.confidence(this.top1Confidence.subtract(this.top2Confidence));
        }
        this.latencyMs = latencyMs;
        this.errorCode = errorCode;
        this.unmappedLabels = unmappedLabels == null ? List.of() : List.copyOf(unmappedLabels);
        this.rawOutput = rawOutput;
        this.visionModelId = visionModelId;
        this.visionModelVersion = visionModelVersion;
        this.asrModelId = asrModelId;
        this.embedModelId = embedModelId;
        this.gradcamObjectKey = gradcamObjectKey;
        this.completed = true;
    }

    public void replaceCandidates(List<CaseCandidate> next) {
        candidates.clear();
        candidates.addAll(next);
    }

    public void replaceSpeechSymptoms(List<CaseSymptom> next) {
        symptoms.removeIf(s -> s.source() == SymptomSource.SPEECH);
        symptoms.addAll(next);
    }

    public void addOfficerSymptom(CaseSymptom symptom) {
        symptoms.add(symptom);
    }

    public UUID id() {
        return id;
    }

    public UUID caseId() {
        return caseId;
    }

    public AiMode mode() {
        return mode;
    }

    public DecisionPath decisionPath() {
        return decisionPath;
    }

    public BigDecimal top1Confidence() {
        return top1Confidence;
    }

    public BigDecimal top2Confidence() {
        return top2Confidence;
    }

    public BigDecimal margin() {
        return margin;
    }

    public int latencyMs() {
        return latencyMs;
    }

    public String errorCode() {
        return errorCode;
    }

    public List<String> unmappedLabels() {
        return unmappedLabels;
    }

    public String rawOutput() {
        return rawOutput;
    }

    public String visionModelId() {
        return visionModelId;
    }

    public String visionModelVersion() {
        return visionModelVersion;
    }

    public String asrModelId() {
        return asrModelId;
    }

    public String embedModelId() {
        return embedModelId;
    }

    public String gradcamObjectKey() {
        return gradcamObjectKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean completed() {
        return completed;
    }

    public List<CaseCandidate> candidates() {
        return List.copyOf(candidates);
    }

    public List<CaseSymptom> symptoms() {
        return List.copyOf(symptoms);
    }
}

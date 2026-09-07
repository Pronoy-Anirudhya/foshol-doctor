package com.rootcause.foshol.analysis.infrastructure.persistence;

import com.rootcause.foshol.analysis.application.port.AnalysisPersistencePort;
import com.rootcause.foshol.analysis.domain.AnalysisRun;
import com.rootcause.foshol.analysis.domain.CaseCandidate;
import com.rootcause.foshol.analysis.domain.CaseSymptom;
import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.SymptomSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAnalysisPersistenceAdapter implements AnalysisPersistencePort {

    private final AnalysisRunJpaRepository runs;
    private final CaseCandidateJpaRepository candidates;
    private final CaseSymptomJpaRepository symptoms;

    public JpaAnalysisPersistenceAdapter(
            AnalysisRunJpaRepository runs,
            CaseCandidateJpaRepository candidates,
            CaseSymptomJpaRepository symptoms) {
        this.runs = runs;
        this.candidates = candidates;
        this.symptoms = symptoms;
    }

    @Override
    public boolean hasCompletedRun(UUID caseId) {
        return runs.existsByCaseIdAndDecisionPathIsNotNull(caseId);
    }

    @Override
    public Optional<AnalysisRun> findLatestByCaseId(UUID caseId) {
        return runs.findFirstByCaseIdOrderByCreatedAtDesc(caseId).map(this::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNewRun(AnalysisRun run, List<CaseCandidate> nextCandidates, List<CaseSymptom> speechSymptoms) {
        candidates.deleteGeneratedForCase(run.caseId());
        symptoms.deleteSpeechForCase(run.caseId());
        AnalysisRunEntity entity = new AnalysisRunEntity();
        entity.setId(run.id());
        entity.setCaseId(run.caseId());
        entity.setMode(run.mode().name());
        entity.setVisionModelId(run.visionModelId());
        entity.setVisionModelVersion(run.visionModelVersion());
        entity.setAsrModelId(run.asrModelId());
        entity.setEmbedModelId(run.embedModelId());
        entity.setTop1Confidence(run.top1Confidence());
        entity.setTop2Confidence(run.top2Confidence());
        entity.setMargin(run.margin());
        entity.setDecisionPath(run.decisionPath() == null ? null : run.decisionPath().name());
        entity.setLatencyMs(run.latencyMs());
        entity.setGradcamObjectKey(run.gradcamObjectKey());
        entity.setUnmappedLabels(toJsonArray(run.unmappedLabels()));
        entity.setRawOutput(run.rawOutput());
        entity.setErrorCode(run.errorCode());
        entity.setCreatedAt(run.createdAt() == null ? Instant.now() : run.createdAt());
        runs.save(entity);
        Instant now = Instant.now();
        for (CaseCandidate candidate : nextCandidates) {
            CaseCandidateEntity row = new CaseCandidateEntity();
            row.setId(candidate.id());
            row.setCaseId(candidate.caseId());
            row.setDiseaseId(candidate.diseaseId());
            row.setConfidence(candidate.confidence());
            row.setRank((short) candidate.rank());
            row.setSource(candidate.source().name());
            row.setCreatedAt(now);
            candidates.save(row);
        }
        for (CaseSymptom symptom : speechSymptoms) {
            CaseSymptomEntity row = toSymptomEntity(symptom, now);
            symptoms.save(row);
        }
    }

    @Override
    @Transactional
    public void addOfficerSymptoms(UUID caseId, List<CaseSymptom> next) {
        Instant now = Instant.now();
        for (CaseSymptom symptom : next) {
            if (!symptoms.existsByCaseIdAndSymptomIdAndSource(caseId, symptom.symptomId(), SymptomSource.OFFICER.name())) {
                symptoms.save(toSymptomEntity(symptom, now));
            }
        }
    }

    @Override
    public boolean existsOfficerSymptom(UUID caseId, UUID symptomId) {
        return symptoms.existsByCaseIdAndSymptomIdAndSource(caseId, symptomId, SymptomSource.OFFICER.name());
    }

    private AnalysisRun toDomain(AnalysisRunEntity entity) {
        AnalysisRun run = new AnalysisRun(
                entity.getId(),
                entity.getCaseId(),
                AiMode.valueOf(entity.getMode()),
                entity.getCreatedAt());
        DecisionPath path = entity.getDecisionPath() == null ? null : DecisionPath.valueOf(entity.getDecisionPath());
        if (path != null) {
            run.complete(
                    path,
                    entity.getTop1Confidence(),
                    entity.getTop2Confidence(),
                    entity.getLatencyMs(),
                    entity.getErrorCode(),
                    List.of(),
                    entity.getRawOutput(),
                    entity.getVisionModelId(),
                    entity.getVisionModelVersion(),
                    entity.getAsrModelId(),
                    entity.getEmbedModelId(),
                    entity.getGradcamObjectKey());
        }
        return run;
    }

    private static CaseSymptomEntity toSymptomEntity(CaseSymptom symptom, Instant now) {
        CaseSymptomEntity row = new CaseSymptomEntity();
        row.setId(symptom.id());
        row.setCaseId(symptom.caseId());
        row.setSymptomId(symptom.symptomId());
        row.setScore(symptom.score());
        row.setSource(symptom.source().name());
        row.setMatcher(symptom.matcher());
        row.setCreatedAt(now);
        return row;
    }

    private static String toJsonArray(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String label : labels) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(label.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        json.append(']');
        return json.toString();
    }
}

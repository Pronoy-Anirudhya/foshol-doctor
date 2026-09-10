package com.rootcause.foshol.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.CandidateSource;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.SymptomSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisRunTest {

    @Test
    void completeRecordsPathAndBecomesTerminal() {
        UUID caseId = UUID.fromString("018f0000-0000-7000-8000-000000000010");
        AnalysisRun run = new AnalysisRun(null, caseId, AiMode.REPLAY, Instant.parse("2026-09-07T00:00:00Z"));
        run.complete(
                DecisionPath.PRIMARY,
                new BigDecimal("0.8000"),
                new BigDecimal("0.1000"),
                120,
                null,
                List.of(),
                "{}",
                "vision",
                "v1",
                null,
                null,
                "cases/x/gradcam/y.png");
        assertThat(run.decisionPath()).isEqualTo(DecisionPath.PRIMARY);
        assertThat(run.margin()).isEqualByComparingTo("0.7000");
        assertThat(run.completed()).isTrue();
        assertThat(run.mode()).isEqualTo(AiMode.REPLAY);
        assertThatThrownBy(() -> run.complete(
                        DecisionPath.PRIMARY,
                        new BigDecimal("0.8000"),
                        new BigDecimal("0.1000"),
                        1,
                        null,
                        List.of(),
                        "{}",
                        "vision",
                        "v1",
                        null,
                        null,
                        null))
                .isInstanceOf(AnalysisInvariantException.class);
    }

    @Test
    void singleCandidateLeavesTop2AndMarginNull() {
        AnalysisRun run = new AnalysisRun(null, UUID.randomUUID(), AiMode.LIVE, Instant.now());
        run.complete(
                DecisionPath.UNDETERMINED,
                new BigDecimal("0.40"),
                null,
                10,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null);
        assertThat(run.top2Confidence()).isNull();
        assertThat(run.margin()).isNull();
    }

    @Test
    void requiresCaseIdAndMode() {
        assertThatThrownBy(() -> new AnalysisRun(null, null, AiMode.REPLAY, Instant.now()))
                .isInstanceOf(AnalysisInvariantException.class);
        assertThatThrownBy(() -> new AnalysisRun(null, UUID.randomUUID(), null, Instant.now()))
                .isInstanceOf(AnalysisInvariantException.class);
    }

    @Test
    void candidateAndSymptomInvariants() {
        UUID caseId = UUID.randomUUID();
        UUID diseaseId = UUID.randomUUID();
        CaseCandidate candidate = new CaseCandidate(
                null, caseId, diseaseId, new BigDecimal("0.81234"), 1, CandidateSource.MODEL);
        assertThat(candidate.confidence()).isEqualByComparingTo("0.8123");
        CaseSymptom officer = CaseSymptom.officer(caseId, UUID.randomUUID());
        assertThat(officer.source()).isEqualTo(SymptomSource.OFFICER);
        assertThat(officer.matcher()).isEqualTo("MANUAL");
        assertThat(officer.score()).isEqualByComparingTo("1.000");
        assertThatThrownBy(() -> new CaseSymptom(
                        null, caseId, UUID.randomUUID(), new BigDecimal("0.5"), SymptomSource.VISION, null))
                .isInstanceOf(AnalysisInvariantException.class);
        assertThatThrownBy(() -> new CaseCandidate(
                        null, caseId, diseaseId, new BigDecimal("1.2"), 1, CandidateSource.MODEL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

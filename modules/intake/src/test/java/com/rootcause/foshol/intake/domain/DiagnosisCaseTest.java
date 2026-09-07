package com.rootcause.foshol.intake.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.intake.domain.vo.ImageId;
import com.rootcause.foshol.intake.domain.vo.ImageQuality;
import com.rootcause.foshol.intake.domain.vo.ObjectKey;
import com.rootcause.foshol.intake.domain.vo.Sha256;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiagnosisCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void submitMarksHighestQualityAsPrimary() {
        CaseImage first = image(false, 1, "0.200");
        CaseImage second = image(false, 2, "0.900");
        DiagnosisCase diagnosisCase = DiagnosisCase.submit(
                CaseId.newId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "DHA",
                "corr",
                NOW,
                List.of(first, second),
                null,
                1,
                3);
        assertThat(diagnosisCase.primaryImage().position()).isEqualTo(2);
        assertThat(diagnosisCase.status()).isEqualTo(CaseStatus.SUBMITTED);
    }

    @Test
    void legalTransitionsEmitEventsAndIdempotentTargetIsNoOp() {
        DiagnosisCase diagnosisCase = submitted();
        CaseStatusChanged changed = diagnosisCase.transitionTo(CaseStatus.ANALYSING, NOW);
        assertThat(changed.toStatus()).isEqualTo(CaseStatus.ANALYSING);
        assertThat(diagnosisCase.transitionTo(CaseStatus.ANALYSING, NOW)).isNull();
    }

    @Test
    void illegalTransitionThrows() {
        DiagnosisCase diagnosisCase = submitted();
        assertThatThrownBy(() -> diagnosisCase.transitionTo(CaseStatus.ADVISED, NOW))
                .isInstanceOf(IllegalCaseTransitionException.class);
    }

    @Test
    void decisionPathIsWrittenOnce() {
        DiagnosisCase diagnosisCase = submitted();
        diagnosisCase.transitionTo(CaseStatus.ANALYSING, NOW);
        diagnosisCase.recordDecisionPath(DecisionPath.SECONDARY);
        diagnosisCase.recordDecisionPath(DecisionPath.PRIMARY);
        assertThat(diagnosisCase.decisionPath()).isEqualTo(DecisionPath.SECONDARY);
    }

    private static DiagnosisCase submitted() {
        return DiagnosisCase.submit(
                CaseId.newId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "DHA",
                "corr",
                NOW,
                List.of(image(true, 1, "0.500")),
                null,
                1,
                3);
    }

    private static CaseImage image(boolean primary, int position, String score) {
        return new CaseImage(
                ImageId.newId(),
                new ObjectKey("cases/x/img/" + position + ".jpg"),
                new ObjectKey("cases/x/img/" + position + ".jpg"),
                "image/jpeg",
                10,
                new ImageQuality(100, 0.5, new BigDecimal(score), 320, 320),
                Sha256.ofUtf8("img-" + position),
                primary,
                position);
    }
}

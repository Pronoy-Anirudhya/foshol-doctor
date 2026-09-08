package com.rootcause.foshol.intake.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.intake.application.command.RecordAnalysisOutcomeCommand;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.intake.domain.CaseImage;
import com.rootcause.foshol.intake.domain.DiagnosisCase;
import com.rootcause.foshol.intake.domain.vo.CaseId;
import com.rootcause.foshol.intake.domain.vo.ImageId;
import com.rootcause.foshol.intake.domain.vo.ImageQuality;
import com.rootcause.foshol.intake.domain.vo.ObjectKey;
import com.rootcause.foshol.intake.domain.vo.Sha256;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RecordAnalysisOutcomeCommandHandlerTest {

    @Mock
    private DiagnosisCaseRepository cases;

    @Mock
    private ApplicationEventPublisher events;

    @Test
    void analysingBecomesInReviewWithDecisionPath() {
        DiagnosisCase diagnosisCase = analysing();
        when(cases.findById(diagnosisCase.id())).thenReturn(Optional.of(diagnosisCase));
        ChangeCaseStatusCommandHandler status = new ChangeCaseStatusCommandHandler(
                cases, events, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        RecordAnalysisOutcomeCommandHandler handler = new RecordAnalysisOutcomeCommandHandler(cases, status);
        handler.handle(new RecordAnalysisOutcomeCommand(diagnosisCase.id().value(), DecisionPath.SECONDARY, false));
        assertThat(diagnosisCase.status()).isEqualTo(CaseStatus.IN_REVIEW);
        assertThat(diagnosisCase.decisionPath()).isEqualTo(DecisionPath.SECONDARY);
        verify(cases, atLeastOnce()).save(diagnosisCase);
    }

    private static DiagnosisCase analysing() {
        CaseImage image = new CaseImage(
                ImageId.newId(),
                new ObjectKey("cases/x/img/y.jpg"),
                new ObjectKey("cases/x/img/y.jpg"),
                "image/jpeg",
                10,
                new ImageQuality(100, 0.5, new BigDecimal("0.500"), 320, 320),
                Sha256.ofUtf8("img"),
                true,
                1);
        DiagnosisCase diagnosisCase = DiagnosisCase.submit(
                CaseId.newId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "DHA",
                "corr",
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of(image),
                null,
                BigDecimal.ONE,
                FieldAreaUnit.DECIMAL,
                null,
                null,
                1,
                3);
        diagnosisCase.transitionTo(CaseStatus.ANALYSING, Instant.parse("2026-01-01T00:00:01Z"));
        return diagnosisCase;
    }
}

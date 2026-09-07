package com.rootcause.foshol.intake.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.events.CaseStatusChanged;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ChangeCaseStatusCommandHandlerTest {

    @Mock
    private DiagnosisCaseRepository cases;

    @Mock
    private ApplicationEventPublisher events;

    @Test
    void publishesStatusChangedOnLegalTransition() {
        DiagnosisCase diagnosisCase = submitted();
        when(cases.findById(diagnosisCase.id())).thenReturn(Optional.of(diagnosisCase));
        ChangeCaseStatusCommandHandler handler = new ChangeCaseStatusCommandHandler(
                cases, events, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        handler.handle(new ChangeCaseStatusCommand(diagnosisCase.id().value(), CaseStatus.ANALYSING));
        ArgumentCaptor<CaseStatusChanged> captor = ArgumentCaptor.forClass(CaseStatusChanged.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().toStatus()).isEqualTo(CaseStatus.ANALYSING);
    }

    private static DiagnosisCase submitted() {
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
        return DiagnosisCase.submit(
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
                1,
                3);
    }
}

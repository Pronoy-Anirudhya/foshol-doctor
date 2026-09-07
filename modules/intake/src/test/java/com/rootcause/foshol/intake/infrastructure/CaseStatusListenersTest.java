package com.rootcause.foshol.intake.infrastructure;

import static org.mockito.Mockito.verify;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.events.CaseSubmitted;
import com.rootcause.foshol.intake.application.command.ChangeCaseStatusCommand;
import com.rootcause.foshol.intake.application.command.ChangeCaseStatusCommandHandler;
import com.rootcause.foshol.intake.application.command.RecordAnalysisOutcomeCommandHandler;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import java.time.Instant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseStatusListenersTest {

    @Mock
    private ChangeCaseStatusCommandHandler statusHandler;

    @Mock
    private RecordAnalysisOutcomeCommandHandler analysisHandler;

    @Mock
    private DiagnosisCaseRepository cases;

    @Mock
    private KnowledgeQueryApi knowledge;

    @Test
    void submittedMovesCaseToAnalysing() {
        UUID caseId = UUID.randomUUID();
        CaseStatusListeners listeners =
                new CaseStatusListeners(statusHandler, analysisHandler, cases, knowledge);
        listeners.onSubmitted(new CaseSubmitted(
                caseId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "rice",
                "DHA",
                List.of(),
                null,
                null,
                null,
                "corr",
                Instant.parse("2026-01-01T00:00:00Z")));
        verify(statusHandler).handle(new ChangeCaseStatusCommand(caseId, CaseStatus.ANALYSING));
    }
}

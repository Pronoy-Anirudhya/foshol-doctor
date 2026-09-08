package com.rootcause.foshol.intake.infrastructure;

import static org.mockito.Mockito.verify;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.common.events.CaseSubmitted;
import com.rootcause.foshol.intake.application.command.ChangeCaseStatusCommand;
import com.rootcause.foshol.intake.application.command.RecordAnalysisOutcomeCommand;
import com.rootcause.foshol.intake.application.port.DiagnosisCaseRepository;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
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
    private CommandHandler<ChangeCaseStatusCommand, Void> statusHandler;

    @Mock
    private CommandHandler<RecordAnalysisOutcomeCommand, Void> analysisHandler;

    @Mock
    private DiagnosisCaseRepository cases;

    @Mock
    private KnowledgeQueryApi knowledge;

    @Test
    void submittedMovesCaseToAnalysing() {
        UUID caseId = UUID.randomUUID();
        CommandBus commands = new CommandBus();
        commands.register(ChangeCaseStatusCommand.class, statusHandler);
        commands.register(RecordAnalysisOutcomeCommand.class, analysisHandler);
        CaseStatusListeners listeners = new CaseStatusListeners(commands, cases, knowledge);
        listeners.onSubmitted(new CaseSubmitted(
                caseId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "rice",
                "DHA",
                "DHK",
                List.of(),
                null,
                null,
                null,
                "corr",
                Instant.parse("2026-01-01T00:00:00Z")));
        verify(statusHandler).handle(new ChangeCaseStatusCommand(caseId, CaseStatus.ANALYSING));
    }
}

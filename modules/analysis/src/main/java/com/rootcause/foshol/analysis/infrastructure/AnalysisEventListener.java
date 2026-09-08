package com.rootcause.foshol.analysis.infrastructure;

import com.rootcause.foshol.analysis.application.command.RunAnalysisCommand;
import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.events.CaseSubmitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnalysisEventListener {

    private final CommandBus commands;

    public AnalysisEventListener(CommandBus commands) {
        this.commands = commands;
    }

    @ApplicationModuleListener
    void onCaseSubmitted(CaseSubmitted event) {
        CorrelationId.set(event.correlationId());
        log.info("event CaseSubmitted caseId={}", event.caseId());
        commands.handle(new RunAnalysisCommand(
                event.caseId(),
                event.farmerId(),
                event.cropId(),
                event.cropCode(),
                event.images(),
                event.audio(),
                event.correlationId()));
    }
}

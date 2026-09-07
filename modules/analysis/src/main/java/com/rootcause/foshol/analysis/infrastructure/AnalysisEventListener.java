package com.rootcause.foshol.analysis.infrastructure;

import com.rootcause.foshol.analysis.application.command.RunAnalysisCommand;
import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.events.CaseSubmitted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AnalysisEventListener {

    private final CommandBus commands;

    public AnalysisEventListener(CommandBus commands) {
        this.commands = commands;
    }

    @ApplicationModuleListener
    void onCaseSubmitted(CaseSubmitted event) {
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

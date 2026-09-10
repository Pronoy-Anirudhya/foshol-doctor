package com.rootcause.foshol.analysis.application.api;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.analysis.application.command.RecordOfficerSymptomsCommand;
import com.rootcause.foshol.analysis.application.port.AnalysisReadRepository;
import com.rootcause.foshol.common.cqrs.CommandBus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AnalysisApiService implements AnalysisApi {

    private final AnalysisReadRepository reads;
    private final CommandBus commands;

    public AnalysisApiService(AnalysisReadRepository reads, CommandBus commands) {
        this.reads = reads;
        this.commands = commands;
    }

    @Override
    public Optional<AnalysisView> findByCaseId(UUID caseId) {
        return reads.findByCaseId(caseId);
    }

    @Override
    public void recordOfficerSymptoms(UUID caseId, List<UUID> symptomIds) {
        commands.handle(new RecordOfficerSymptomsCommand(caseId, symptomIds));
    }
}

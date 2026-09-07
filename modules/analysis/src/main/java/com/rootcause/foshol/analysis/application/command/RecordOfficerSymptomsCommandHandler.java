package com.rootcause.foshol.analysis.application.command;

import com.rootcause.foshol.analysis.application.port.AnalysisPersistencePort;
import com.rootcause.foshol.analysis.domain.AnalysisNotFoundException;
import com.rootcause.foshol.analysis.domain.CaseSymptom;
import com.rootcause.foshol.analysis.domain.UnknownSymptomException;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordOfficerSymptomsCommandHandler {

    private final AnalysisPersistencePort persistence;
    private final KnowledgeQueryApi knowledge;

    public RecordOfficerSymptomsCommandHandler(
            AnalysisPersistencePort persistence, KnowledgeQueryApi knowledge) {
        this.persistence = persistence;
        this.knowledge = knowledge;
    }

    @Transactional
    public void handle(RecordOfficerSymptomsCommand command) {
        if (!persistence.hasCompletedRun(command.caseId())) {
            throw new AnalysisNotFoundException(ErrorCodes.ERR_ANALYSIS_NOT_FOUND);
        }
        Set<UUID> known = new HashSet<>();
        for (SymptomRefView view : knowledge.listSymptoms()) {
            known.add(view.id());
        }
        for (UUID symptomId : command.symptomIds()) {
            if (!known.contains(symptomId)) {
                throw new UnknownSymptomException(ErrorCodes.ERR_UNKNOWN_SYMPTOM);
            }
        }
        List<CaseSymptom> toInsert = new ArrayList<>();
        for (UUID symptomId : command.symptomIds()) {
            if (!persistence.existsOfficerSymptom(command.caseId(), symptomId)) {
                toInsert.add(CaseSymptom.officer(command.caseId(), symptomId));
            }
        }
        persistence.addOfficerSymptoms(command.caseId(), toInsert);
    }
}

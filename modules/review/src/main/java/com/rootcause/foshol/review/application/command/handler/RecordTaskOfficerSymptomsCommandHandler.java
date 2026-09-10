package com.rootcause.foshol.review.application.command.handler;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import com.rootcause.foshol.review.application.command.ReviewDistrictGuard;
import com.rootcause.foshol.review.application.command.RecordOfficerSymptomsCommand;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordTaskOfficerSymptomsCommandHandler implements CommandHandler<RecordOfficerSymptomsCommand, Void> {

    @Override
    public Class<RecordOfficerSymptomsCommand> commandType() {
        return RecordOfficerSymptomsCommand.class;
    }

    private final ReviewTaskRepository tasks;
    private final AnalysisApi analysisApi;
    private final KnowledgeQueryApi knowledge;
    private final ReviewDistrictGuard districtGuard;
    private final Clock clock;
    private final Duration claimTtl;

    public RecordTaskOfficerSymptomsCommandHandler(
            ReviewTaskRepository tasks,
            AnalysisApi analysisApi,
            KnowledgeQueryApi knowledge,
            ReviewDistrictGuard districtGuard,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_CLAIM_TTL + ":PT15M}") Duration claimTtl) {
        this.tasks = tasks;
        this.analysisApi = analysisApi;
        this.knowledge = knowledge;
        this.districtGuard = districtGuard;
        this.clock = clock;
        this.claimTtl = claimTtl;
    }

    @Transactional
    @Override
    public Void handle(RecordOfficerSymptomsCommand command) {
        districtGuard.requireTaskInCallerDistrict(command.officerId(), command.taskId());
        ReviewTask task = tasks.findById(command.taskId()).orElseThrow(ReviewException::taskNotFound);
        task.requireLiveClaim(command.officerId(), clock.instant(), claimTtl);
        Set<UUID> known = knowledge.listSymptoms().stream().map(SymptomRefView::id).collect(Collectors.toSet());
        for (UUID symptomId : command.symptomIds()) {
            if (!known.contains(symptomId)) {
                throw ReviewException.unknownSymptom();
            }
        }
        analysisApi.recordOfficerSymptoms(task.caseId(), command.symptomIds());
        return null;
    }
}

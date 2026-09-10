package com.rootcause.foshol.analysis.application.query.handler;

import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.analysis.application.query.AnalysisDetailQuery;
import com.rootcause.foshol.analysis.application.port.AnalysisReadRepository;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class AnalysisDetailQueryHandler implements QueryHandler<AnalysisDetailQuery, Optional<AnalysisView>> {

    @Override
    public Class<AnalysisDetailQuery> queryType() {
        return AnalysisDetailQuery.class;
    }

    private final AnalysisReadRepository reads;
    private final CaseIntakeApi intake;

    public AnalysisDetailQueryHandler(AnalysisReadRepository reads, CaseIntakeApi intake) {
        this.reads = reads;
        this.intake = intake;
    }

    @Override
    public Optional<AnalysisView> handle(AnalysisDetailQuery query) {
        if (!visible(query.caseId(), query.callerId(), query.role())) {
            return Optional.empty();
        }
        return reads.findByCaseId(query.caseId());
    }

    private boolean visible(UUID caseId, UUID callerId, Role role) {
        if (role == Role.OFFICER || role == Role.ADMIN) {
            return intake.officerSharesDistrict(caseId, callerId);
        }
        if (role == Role.FARMER && callerId != null) {
            return intake.isOwnedBy(caseId, callerId);
        }
        return false;
    }
}

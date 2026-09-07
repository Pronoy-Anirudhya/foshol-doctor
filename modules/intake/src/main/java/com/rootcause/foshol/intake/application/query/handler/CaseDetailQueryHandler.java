package com.rootcause.foshol.intake.application.query.handler;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.query.CaseDetailQuery;
import com.rootcause.foshol.intake.application.query.CaseDetailView;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseDetailQueryHandler implements QueryHandler<CaseDetailQuery, CaseDetailView> {

    @Override
    public Class<CaseDetailQuery> queryType() {
        return CaseDetailQuery.class;
    }

    private final CaseQueryPort queries;

    public CaseDetailQueryHandler(CaseQueryPort queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    @Override
    public CaseDetailView handle(CaseDetailQuery query) {
        java.util.UUID owner = queries
                .findFarmerId(query.caseId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found."));
        if (query.callerRole() != Role.OFFICER
                && query.callerRole() != Role.ADMIN
                && !owner.equals(query.callerId())) {
            throw new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found.");
        }
        return queries
                .findDetail(query.caseId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found."));
    }
}

package com.rootcause.foshol.intake.application.query.handler;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.intake.domain.IntakeException;
import com.rootcause.foshol.intake.application.query.StaffRegionAccess;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.query.CaseDetailQuery;
import com.rootcause.foshol.intake.application.query.CaseDetailView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseDetailQueryHandler implements QueryHandler<CaseDetailQuery, CaseDetailView> {

    @Override
    public Class<CaseDetailQuery> queryType() {
        return CaseDetailQuery.class;
    }

    private final CaseQueryPort queries;
    private final StaffRegionAccess staffRegion;

    public CaseDetailQueryHandler(CaseQueryPort queries, StaffRegionAccess staffRegion) {
        this.queries = queries;
        this.staffRegion = staffRegion;
    }

    @Transactional(readOnly = true)
    @Override
    public CaseDetailView handle(CaseDetailQuery query) {
        java.util.UUID owner = queries
                .findFarmerId(query.caseId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found."));
        if (query.callerRole() == Role.FARMER) {
            if (!owner.equals(query.callerId())) {
                throw new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found.");
            }
        } else if (!staffRegion.allows(query.callerRole(), query.callerId(), query.caseId())) {
            throw new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found.");
        }
        return queries
                .findDetail(query.caseId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found."));
    }
}

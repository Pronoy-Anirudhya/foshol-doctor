package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseDetailQueryHandler {

    private final CaseQueryPort queries;

    public CaseDetailQueryHandler(CaseQueryPort queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
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

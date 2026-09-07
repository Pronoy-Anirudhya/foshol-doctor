package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerCaseListQueryHandler {

    private final CaseQueryPort queries;

    public FarmerCaseListQueryHandler(CaseQueryPort queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public PageResult<FarmerCaseRow> handle(FarmerCaseListQuery query) {
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        int page = Math.max(query.page(), 0);
        return queries.listHistory(query.farmerId(), page, size);
    }
}

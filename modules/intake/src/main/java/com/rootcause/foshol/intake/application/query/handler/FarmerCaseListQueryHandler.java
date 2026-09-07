package com.rootcause.foshol.intake.application.query.handler;

import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.query.FarmerCaseListQuery;
import com.rootcause.foshol.intake.application.query.FarmerCaseRow;
import com.rootcause.foshol.intake.application.query.PageResult;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerCaseListQueryHandler implements QueryHandler<FarmerCaseListQuery, PageResult<FarmerCaseRow>> {

    @Override
    public Class<FarmerCaseListQuery> queryType() {
        return FarmerCaseListQuery.class;
    }

    private final CaseQueryPort queries;

    public FarmerCaseListQueryHandler(CaseQueryPort queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<FarmerCaseRow> handle(FarmerCaseListQuery query) {
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        int page = Math.max(query.page(), 0);
        return queries.listHistory(query.farmerId(), page, size);
    }
}

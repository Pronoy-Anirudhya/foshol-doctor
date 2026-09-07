package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.knowledge.api.SymptomMatchApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchRequest;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import com.rootcause.foshol.knowledge.application.query.SymptomMatchQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SymptomMatchApiAdapter implements SymptomMatchApi {

    private final QueryBus queries;

    public SymptomMatchApiAdapter(QueryBus queries) {
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public SymptomMatchResult match(SymptomMatchRequest request) {
        return queries.handle(new SymptomMatchQuery(request));
    }
}

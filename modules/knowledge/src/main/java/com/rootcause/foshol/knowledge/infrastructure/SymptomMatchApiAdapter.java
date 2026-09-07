package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.knowledge.api.SymptomMatchApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchRequest;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import com.rootcause.foshol.knowledge.application.query.SymptomMatchQuery;
import com.rootcause.foshol.knowledge.application.query.SymptomMatchQueryHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SymptomMatchApiAdapter implements SymptomMatchApi {

    private final SymptomMatchQueryHandler handler;

    public SymptomMatchApiAdapter(SymptomMatchQueryHandler handler) {
        this.handler = handler;
    }

    @Override
    @Transactional(readOnly = true)
    public SymptomMatchResult match(SymptomMatchRequest request) {
        return handler.handle(new SymptomMatchQuery(request));
    }
}

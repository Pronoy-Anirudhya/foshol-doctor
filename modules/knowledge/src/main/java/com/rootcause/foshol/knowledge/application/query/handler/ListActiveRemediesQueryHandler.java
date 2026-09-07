package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.ListActiveRemediesQuery;
import com.rootcause.foshol.knowledge.application.query.RemedyReadModel;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListActiveRemediesQueryHandler implements QueryHandler<ListActiveRemediesQuery, List<RemedyReadModel>> {

    @Override
    public Class<ListActiveRemediesQuery> queryType() {
        return ListActiveRemediesQuery.class;
    }

    private final KnowledgeReadPort reads;

    public ListActiveRemediesQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public List<RemedyReadModel> handle(ListActiveRemediesQuery query) {
        if (query.diseaseId() == null) {
            return List.of();
        }
        return reads.listActiveRemedies(query.diseaseId());
    }
}

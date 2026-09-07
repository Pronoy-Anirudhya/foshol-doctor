package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.ListSymptomsQuery;
import com.rootcause.foshol.knowledge.application.query.SymptomReadModel;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSymptomsQueryHandler implements QueryHandler<ListSymptomsQuery, List<SymptomReadModel>> {

    @Override
    public Class<ListSymptomsQuery> queryType() {
        return ListSymptomsQuery.class;
    }

    private final KnowledgeReadPort reads;

    public ListSymptomsQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SymptomReadModel> handle(ListSymptomsQuery query) {
        return reads.listSymptoms();
    }
}

package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.ListDiseasesByCropQuery;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListDiseasesByCropQueryHandler implements QueryHandler<ListDiseasesByCropQuery, List<DiseaseReadModel>> {

    @Override
    public Class<ListDiseasesByCropQuery> queryType() {
        return ListDiseasesByCropQuery.class;
    }

    private final KnowledgeReadPort reads;

    public ListDiseasesByCropQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public List<DiseaseReadModel> handle(ListDiseasesByCropQuery query) {
        if (query.cropId() == null) {
            return List.of();
        }
        return reads.listDiseasesByCrop(query.cropId());
    }
}

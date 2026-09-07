package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.FindDiseaseByIdQuery;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindDiseaseByIdQueryHandler implements QueryHandler<FindDiseaseByIdQuery, Optional<DiseaseReadModel>> {

    @Override
    public Class<FindDiseaseByIdQuery> queryType() {
        return FindDiseaseByIdQuery.class;
    }

    private final KnowledgeReadPort reads;

    public FindDiseaseByIdQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<DiseaseReadModel> handle(FindDiseaseByIdQuery query) {
        if (query.diseaseId() == null) {
            return Optional.empty();
        }
        return reads.findDiseaseById(query.diseaseId());
    }
}

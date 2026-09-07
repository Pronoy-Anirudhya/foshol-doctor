package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.GetDiseaseRemediesQuery;
import com.rootcause.foshol.knowledge.application.query.RemedyReadModel;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetDiseaseRemediesQueryHandler implements QueryHandler<GetDiseaseRemediesQuery, List<RemedyReadModel>> {

    @Override
    public Class<GetDiseaseRemediesQuery> queryType() {
        return GetDiseaseRemediesQuery.class;
    }

    private final KnowledgeReadPort reads;

    public GetDiseaseRemediesQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public List<RemedyReadModel> handle(GetDiseaseRemediesQuery query) {
        if (query.diseaseId() == null || reads.findDiseaseById(query.diseaseId()).isEmpty()) {
            throw new KnowledgeException(ErrorCodes.ERR_DISEASE_NOT_FOUND, 404, "Disease not found.");
        }
        return reads.listActiveRemedies(query.diseaseId());
    }
}

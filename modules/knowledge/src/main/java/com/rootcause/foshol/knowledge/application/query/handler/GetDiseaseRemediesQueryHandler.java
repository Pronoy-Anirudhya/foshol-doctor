package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetDiseaseRemediesQueryHandler {

    private final KnowledgeReadPort reads;

    public GetDiseaseRemediesQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public List<RemedyReadModel> handle(GetDiseaseRemediesQuery query) {
        if (query.diseaseId() == null || reads.findDiseaseById(query.diseaseId()).isEmpty()) {
            throw new KnowledgeException(ErrorCodes.ERR_DISEASE_NOT_FOUND, 404, "Disease not found.");
        }
        return reads.listActiveRemedies(query.diseaseId());
    }
}

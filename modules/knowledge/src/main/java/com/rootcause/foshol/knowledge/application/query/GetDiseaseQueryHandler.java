package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetDiseaseQueryHandler {

    private final KnowledgeReadPort reads;

    public GetDiseaseQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public DiseaseReadModel handle(GetDiseaseQuery query) {
        if (query.diseaseId() == null) {
            throw new KnowledgeException(ErrorCodes.ERR_DISEASE_NOT_FOUND, 404, "Disease not found.");
        }
        return reads.findDiseaseById(query.diseaseId())
                .orElseThrow(() -> new KnowledgeException(
                        ErrorCodes.ERR_DISEASE_NOT_FOUND, 404, "Disease not found."));
    }
}

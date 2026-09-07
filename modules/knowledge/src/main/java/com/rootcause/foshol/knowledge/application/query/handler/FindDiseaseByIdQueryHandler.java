package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindDiseaseByIdQueryHandler {

    private final KnowledgeReadPort reads;

    public FindDiseaseByIdQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public Optional<DiseaseReadModel> handle(FindDiseaseByIdQuery query) {
        if (query.diseaseId() == null) {
            return Optional.empty();
        }
        return reads.findDiseaseById(query.diseaseId());
    }
}

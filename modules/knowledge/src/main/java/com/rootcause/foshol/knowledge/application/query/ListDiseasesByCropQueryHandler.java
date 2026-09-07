package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListDiseasesByCropQueryHandler {

    private final KnowledgeReadPort reads;

    public ListDiseasesByCropQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public List<DiseaseReadModel> handle(ListDiseasesByCropQuery query) {
        if (query.cropId() == null) {
            return List.of();
        }
        return reads.listDiseasesByCrop(query.cropId());
    }
}

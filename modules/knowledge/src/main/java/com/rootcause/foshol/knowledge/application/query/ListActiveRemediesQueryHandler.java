package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListActiveRemediesQueryHandler {

    private final KnowledgeReadPort reads;

    public ListActiveRemediesQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public List<RemedyReadModel> handle(ListActiveRemediesQuery query) {
        if (query.diseaseId() == null) {
            return List.of();
        }
        return reads.listActiveRemedies(query.diseaseId());
    }
}

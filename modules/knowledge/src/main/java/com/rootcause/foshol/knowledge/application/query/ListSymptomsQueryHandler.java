package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSymptomsQueryHandler {

    private final KnowledgeReadPort reads;

    public ListSymptomsQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public List<SymptomReadModel> handle(ListSymptomsQuery query) {
        return reads.listSymptoms();
    }
}

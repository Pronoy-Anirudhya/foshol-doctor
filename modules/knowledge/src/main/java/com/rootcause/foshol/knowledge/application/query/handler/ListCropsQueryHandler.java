package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListCropsQueryHandler {

    private final KnowledgeReadPort reads;

    public ListCropsQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public List<CropReadModel> handle(ListCropsQuery query) {
        return reads.listCrops();
    }
}

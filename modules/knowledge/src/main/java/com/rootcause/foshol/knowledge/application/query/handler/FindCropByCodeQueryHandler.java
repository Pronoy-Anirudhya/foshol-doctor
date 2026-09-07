package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindCropByCodeQueryHandler {

    private final KnowledgeReadPort reads;

    public FindCropByCodeQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public Optional<CropReadModel> handle(FindCropByCodeQuery query) {
        if (query.code() == null) {
            return Optional.empty();
        }
        return reads.findCropByCode(query.code());
    }
}

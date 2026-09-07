package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindCropByIdQueryHandler {

    private final KnowledgeReadPort reads;

    public FindCropByIdQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public Optional<CropReadModel> handle(FindCropByIdQuery query) {
        if (query.cropId() == null) {
            return Optional.empty();
        }
        return reads.findCropById(query.cropId());
    }
}

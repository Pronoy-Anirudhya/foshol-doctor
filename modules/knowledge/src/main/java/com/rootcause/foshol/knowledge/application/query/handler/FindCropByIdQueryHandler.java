package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.FindCropByIdQuery;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindCropByIdQueryHandler implements QueryHandler<FindCropByIdQuery, Optional<CropReadModel>> {

    @Override
    public Class<FindCropByIdQuery> queryType() {
        return FindCropByIdQuery.class;
    }

    private final KnowledgeReadPort reads;

    public FindCropByIdQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<CropReadModel> handle(FindCropByIdQuery query) {
        if (query.cropId() == null) {
            return Optional.empty();
        }
        return reads.findCropById(query.cropId());
    }
}

package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.FindCropByCodeQuery;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindCropByCodeQueryHandler implements QueryHandler<FindCropByCodeQuery, Optional<CropReadModel>> {

    @Override
    public Class<FindCropByCodeQuery> queryType() {
        return FindCropByCodeQuery.class;
    }

    private final KnowledgeReadPort reads;

    public FindCropByCodeQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<CropReadModel> handle(FindCropByCodeQuery query) {
        if (query.code() == null) {
            return Optional.empty();
        }
        return reads.findCropByCode(query.code());
    }
}

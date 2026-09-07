package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.ListCropsQuery;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListCropsQueryHandler implements QueryHandler<ListCropsQuery, List<CropReadModel>> {

    @Override
    public Class<ListCropsQuery> queryType() {
        return ListCropsQuery.class;
    }

    private final KnowledgeReadPort reads;

    public ListCropsQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public List<CropReadModel> handle(ListCropsQuery query) {
        return reads.listCrops();
    }
}

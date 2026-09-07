package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.GetCropDiseasesQuery;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCropDiseasesQueryHandler implements QueryHandler<GetCropDiseasesQuery, List<DiseaseReadModel>> {

    @Override
    public Class<GetCropDiseasesQuery> queryType() {
        return GetCropDiseasesQuery.class;
    }

    private final KnowledgeReadPort reads;

    public GetCropDiseasesQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    @Override
    public List<DiseaseReadModel> handle(GetCropDiseasesQuery query) {
        if (query.cropId() == null || reads.findCropById(query.cropId()).isEmpty()) {
            throw new KnowledgeException(ErrorCodes.ERR_CROP_NOT_FOUND, 404, "Crop not found.");
        }
        return reads.listDiseasesByCrop(query.cropId());
    }
}

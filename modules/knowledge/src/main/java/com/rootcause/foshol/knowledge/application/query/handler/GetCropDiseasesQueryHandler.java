package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCropDiseasesQueryHandler {

    private final KnowledgeReadPort reads;

    public GetCropDiseasesQueryHandler(KnowledgeReadPort reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public List<DiseaseReadModel> handle(GetCropDiseasesQuery query) {
        if (query.cropId() == null || reads.findCropById(query.cropId()).isEmpty()) {
            throw new KnowledgeException(ErrorCodes.ERR_CROP_NOT_FOUND, 404, "Crop not found.");
        }
        return reads.listDiseasesByCrop(query.cropId());
    }
}

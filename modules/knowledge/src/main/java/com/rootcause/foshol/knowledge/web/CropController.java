package com.rootcause.foshol.knowledge.web;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.GetCropDiseasesQuery;
import com.rootcause.foshol.knowledge.application.query.ListCropsQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crops")
@PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
public class CropController {

    private final QueryBus queries;

    public CropController(QueryBus queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<CropResponse> list() {
        List<CropReadModel> crops = queries.handle(new ListCropsQuery());
        return crops.stream().map(KnowledgeWebMapper::toCropResponse).toList();
    }

    @GetMapping("/{cropId}/diseases")
    public List<DiseaseResponse> diseases(@PathVariable UUID cropId) {
        List<DiseaseReadModel> diseases = queries.handle(new GetCropDiseasesQuery(cropId));
        return diseases.stream().map(KnowledgeWebMapper::toDiseaseResponse).toList();
    }
}

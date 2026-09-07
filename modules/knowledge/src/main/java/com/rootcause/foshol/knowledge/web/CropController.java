package com.rootcause.foshol.knowledge.web;

import com.rootcause.foshol.knowledge.application.query.GetCropDiseasesQuery;
import com.rootcause.foshol.knowledge.application.query.GetCropDiseasesQueryHandler;
import com.rootcause.foshol.knowledge.application.query.ListCropsQuery;
import com.rootcause.foshol.knowledge.application.query.ListCropsQueryHandler;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crops")
public class CropController {

    private final ListCropsQueryHandler listCrops;
    private final GetCropDiseasesQueryHandler cropDiseases;

    public CropController(ListCropsQueryHandler listCrops, GetCropDiseasesQueryHandler cropDiseases) {
        this.listCrops = listCrops;
        this.cropDiseases = cropDiseases;
    }

    @GetMapping
    public List<CropResponse> list() {
        return listCrops.handle(new ListCropsQuery()).stream()
                .map(KnowledgeWebMapper::toCropResponse)
                .toList();
    }

    @GetMapping("/{cropId}/diseases")
    public List<DiseaseResponse> diseases(@PathVariable UUID cropId) {
        return cropDiseases.handle(new GetCropDiseasesQuery(cropId)).stream()
                .map(KnowledgeWebMapper::toDiseaseResponse)
                .toList();
    }
}

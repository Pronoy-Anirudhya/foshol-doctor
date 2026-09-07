package com.rootcause.foshol.knowledge.web;

import com.rootcause.foshol.knowledge.application.query.GetDiseaseQuery;
import com.rootcause.foshol.knowledge.application.query.GetDiseaseQueryHandler;
import com.rootcause.foshol.knowledge.application.query.GetDiseaseRemediesQuery;
import com.rootcause.foshol.knowledge.application.query.GetDiseaseRemediesQueryHandler;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diseases")
public class DiseaseController {

    private final GetDiseaseQueryHandler findDisease;
    private final GetDiseaseRemediesQueryHandler listRemedies;

    public DiseaseController(GetDiseaseQueryHandler findDisease, GetDiseaseRemediesQueryHandler listRemedies) {
        this.findDisease = findDisease;
        this.listRemedies = listRemedies;
    }

    @GetMapping("/{diseaseId}")
    public DiseaseResponse get(@PathVariable UUID diseaseId) {
        return KnowledgeWebMapper.toDiseaseResponse(findDisease.handle(new GetDiseaseQuery(diseaseId)));
    }

    @GetMapping("/{diseaseId}/remedies")
    public List<RemedyResponse> remedies(@PathVariable UUID diseaseId) {
        return listRemedies.handle(new GetDiseaseRemediesQuery(diseaseId)).stream()
                .map(KnowledgeWebMapper::toRemedyResponse)
                .toList();
    }
}

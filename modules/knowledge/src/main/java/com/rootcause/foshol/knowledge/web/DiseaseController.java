package com.rootcause.foshol.knowledge.web;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.GetDiseaseQuery;
import com.rootcause.foshol.knowledge.application.query.GetDiseaseRemediesQuery;
import com.rootcause.foshol.knowledge.application.query.RemedyReadModel;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diseases")
@PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
public class DiseaseController {

    private final QueryBus queries;

    public DiseaseController(QueryBus queries) {
        this.queries = queries;
    }

    @GetMapping("/{diseaseId}")
    public DiseaseResponse get(@PathVariable UUID diseaseId) {
        DiseaseReadModel disease = queries.handle(new GetDiseaseQuery(diseaseId));
        return KnowledgeWebMapper.toDiseaseResponse(disease);
    }

    @GetMapping("/{diseaseId}/remedies")
    public List<RemedyResponse> remedies(@PathVariable UUID diseaseId) {
        List<RemedyReadModel> remedies = queries.handle(new GetDiseaseRemediesQuery(diseaseId));
        return remedies.stream().map(KnowledgeWebMapper::toRemedyResponse).toList();
    }
}

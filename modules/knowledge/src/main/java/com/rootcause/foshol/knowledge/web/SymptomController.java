package com.rootcause.foshol.knowledge.web;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.knowledge.application.query.ListSymptomsQuery;
import com.rootcause.foshol.knowledge.application.query.SymptomReadModel;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/symptoms")
public class SymptomController {

    private final QueryBus queries;

    public SymptomController(QueryBus queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<SymptomResponse> listSymptoms() {
        List<SymptomReadModel> symptoms = queries.handle(new ListSymptomsQuery());
        return symptoms.stream().map(KnowledgeWebMapper::toSymptomResponse).toList();
    }
}

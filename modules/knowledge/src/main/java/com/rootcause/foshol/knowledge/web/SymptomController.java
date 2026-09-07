package com.rootcause.foshol.knowledge.web;

import com.rootcause.foshol.knowledge.application.query.ListSymptomsQuery;
import com.rootcause.foshol.knowledge.application.query.ListSymptomsQueryHandler;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/symptoms")
public class SymptomController {

    private final ListSymptomsQueryHandler listSymptoms;

    public SymptomController(ListSymptomsQueryHandler listSymptoms) {
        this.listSymptoms = listSymptoms;
    }

    @GetMapping
    public List<SymptomResponse> listSymptoms() {
        return listSymptoms.handle(new ListSymptomsQuery()).stream()
                .map(KnowledgeWebMapper::toSymptomResponse)
                .toList();
    }
}

package com.rootcause.foshol.knowledge.infrastructure.persistence;

import com.rootcause.foshol.knowledge.application.port.KnowledgeContentPort;
import com.rootcause.foshol.knowledge.application.port.SymptomCatalog;
import com.rootcause.foshol.knowledge.domain.SymptomRef;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class InMemorySymptomCatalog implements SymptomCatalog {

    private final Map<UUID, SymptomRef> byId;

    public InMemorySymptomCatalog(KnowledgeContentPort content) {
        this.byId = content.loadLiveSymptoms().stream()
                .collect(Collectors.toUnmodifiableMap(SymptomRef::id, Function.identity()));
    }

    @Override
    public Optional<SymptomRef> findLive(UUID symptomId) {
        if (symptomId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(symptomId));
    }
}

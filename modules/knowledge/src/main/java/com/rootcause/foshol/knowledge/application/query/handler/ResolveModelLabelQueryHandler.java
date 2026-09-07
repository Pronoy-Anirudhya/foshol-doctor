package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.application.port.ModelLabelIndex;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ResolveModelLabelQueryHandler {

    private final ModelLabelIndex labels;

    public ResolveModelLabelQueryHandler(ModelLabelIndex labels) {
        this.labels = labels;
    }

    public Optional<UUID> handle(ResolveModelLabelQuery query) {
        if (query.modelId() == null || query.modelVersion() == null || query.rawLabel() == null) {
            return Optional.empty();
        }
        return labels.resolve(query.modelId(), query.modelVersion(), query.rawLabel());
    }
}

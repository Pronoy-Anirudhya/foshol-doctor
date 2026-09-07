package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.knowledge.application.port.KnowledgeContentPort;
import com.rootcause.foshol.knowledge.application.port.LabelKey;
import com.rootcause.foshol.knowledge.application.port.ModelLabelIndex;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InMemoryModelLabelIndex implements ModelLabelIndex {

    private final Map<LabelKey, UUID> labels;

    public InMemoryModelLabelIndex(KnowledgeContentPort content) {
        this.labels = Map.copyOf(content.loadLabelMap());
    }

    @Override
    public Optional<UUID> resolve(String modelId, String modelVersion, String rawLabel) {
        if (modelId == null || modelVersion == null || rawLabel == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(labels.get(new LabelKey(modelId, modelVersion, rawLabel)));
    }
}

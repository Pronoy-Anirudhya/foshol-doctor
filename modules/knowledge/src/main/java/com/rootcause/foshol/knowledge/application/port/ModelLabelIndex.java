package com.rootcause.foshol.knowledge.application.port;

import java.util.Optional;
import java.util.UUID;

public interface ModelLabelIndex {

    Optional<UUID> resolve(String modelId, String modelVersion, String rawLabel);
}

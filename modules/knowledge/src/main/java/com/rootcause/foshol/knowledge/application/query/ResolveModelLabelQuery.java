package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.cqrs.Query;

public record ResolveModelLabelQuery(String modelId, String modelVersion, String rawLabel) implements Query {}

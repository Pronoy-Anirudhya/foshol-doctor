package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record ListActiveRemediesQuery(UUID diseaseId) implements Query {}

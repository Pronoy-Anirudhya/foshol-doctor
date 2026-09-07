package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import com.rootcause.foshol.knowledge.api.SymptomMatchRequest;

public record SymptomMatchQuery(SymptomMatchRequest request) implements Query {}

package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import com.rootcause.foshol.review.application.Actor;
import java.util.UUID;

public record CaseAdvisoryQuery(UUID caseId, Actor actor) implements Query {}

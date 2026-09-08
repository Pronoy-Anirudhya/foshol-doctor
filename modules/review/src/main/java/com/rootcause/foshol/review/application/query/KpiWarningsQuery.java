package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record KpiWarningsQuery(UUID callerId) implements Query {}

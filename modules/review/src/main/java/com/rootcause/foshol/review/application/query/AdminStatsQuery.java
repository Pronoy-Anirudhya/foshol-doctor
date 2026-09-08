package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record AdminStatsQuery(UUID callerId) implements Query {}

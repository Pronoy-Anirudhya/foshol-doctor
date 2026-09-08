package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record OfficerQueueQuery(
        String state,
        boolean mine,
        UUID officerId,
        String districtCode,
        int page,
        int size,
        String sort,
        String order) implements Query {}

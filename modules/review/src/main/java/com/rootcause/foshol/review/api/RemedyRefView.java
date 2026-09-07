package com.rootcause.foshol.review.api;

import com.rootcause.foshol.common.RemedyType;
import java.util.List;
import java.util.UUID;

public record RemedyRefView(
        UUID remedyId,
        RemedyType type,
        String titleBn,
        List<String> stepsBn,
        String dosageBn,
        Integer phiDays,
        String sourceRef) {}

package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.RemedyType;
import java.util.List;
import java.util.UUID;

public record RemedyReadModel(
        UUID id,
        UUID diseaseId,
        RemedyType type,
        String titleBn,
        List<String> stepsBn,
        String dosageBn,
        Integer phiDays,
        String costTier,
        String efficacy,
        String sourceRef,
        int displayOrder) {}

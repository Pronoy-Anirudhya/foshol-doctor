package com.rootcause.foshol.knowledge.web;

import com.rootcause.foshol.common.enums.Severity;
import java.util.UUID;

public record DiseaseResponse(
        UUID id,
        UUID cropId,
        String code,
        String nameBn,
        String nameEn,
        boolean nameEnFallback,
        String descriptionBn,
        Severity severity,
        boolean healthy) {}

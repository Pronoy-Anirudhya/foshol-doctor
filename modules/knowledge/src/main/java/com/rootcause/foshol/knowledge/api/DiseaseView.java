package com.rootcause.foshol.knowledge.api;

import com.rootcause.foshol.common.Severity;
import java.util.UUID;

public record DiseaseView(
        UUID id,
        UUID cropId,
        String code,
        String nameBn,
        String nameEn,
        String descriptionBn,
        Severity severity,
        boolean healthy) {}

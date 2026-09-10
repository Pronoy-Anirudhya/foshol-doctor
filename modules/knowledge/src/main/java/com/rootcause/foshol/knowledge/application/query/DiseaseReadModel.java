package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.enums.Severity;
import java.util.UUID;

public record DiseaseReadModel(
        UUID id,
        UUID cropId,
        String code,
        String nameBn,
        String nameEn,
        String descriptionBn,
        Severity severity,
        boolean healthy) {}

package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.enums.SymptomSource;
import java.math.BigDecimal;
import java.util.UUID;

public record SymptomView(
        UUID symptomId,
        String symptomCode,
        String nameBn,
        BigDecimal score,
        SymptomSource source,
        String matcher) {}

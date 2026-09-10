package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.enums.SymptomSource;
import com.rootcause.foshol.common.util.CatalogueLocale;
import java.math.BigDecimal;
import java.util.UUID;

public record SymptomView(
        UUID symptomId,
        String symptomCode,
        String nameBn,
        String nameEn,
        boolean nameEnFallback,
        BigDecimal score,
        SymptomSource source,
        String matcher) {

    public static SymptomView of(
            UUID symptomId,
            String symptomCode,
            String nameBn,
            String nameEn,
            BigDecimal score,
            SymptomSource source,
            String matcher) {
        boolean fallback = CatalogueLocale.enFallback(nameEn);
        return new SymptomView(
                symptomId,
                symptomCode,
                nameBn,
                CatalogueLocale.enOrBn(nameEn, nameBn),
                fallback,
                score,
                source,
                matcher);
    }
}

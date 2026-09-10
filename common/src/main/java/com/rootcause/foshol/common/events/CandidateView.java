package com.rootcause.foshol.common.events;

import com.rootcause.foshol.common.enums.CandidateSource;
import com.rootcause.foshol.common.util.CatalogueLocale;
import java.math.BigDecimal;
import java.util.UUID;

public record CandidateView(
        UUID diseaseId,
        String diseaseCode,
        String diseaseNameBn,
        String diseaseNameEn,
        boolean diseaseNameEnFallback,
        BigDecimal confidence,
        int rank,
        CandidateSource source) {

    public static CandidateView of(
            UUID diseaseId,
            String diseaseCode,
            String nameBn,
            String nameEn,
            BigDecimal confidence,
            int rank,
            CandidateSource source) {
        boolean fallback = CatalogueLocale.enFallback(nameEn);
        return new CandidateView(
                diseaseId,
                diseaseCode,
                nameBn,
                CatalogueLocale.enOrBn(nameEn, nameBn),
                fallback,
                confidence,
                rank,
                source);
    }
}

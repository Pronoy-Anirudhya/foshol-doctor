package com.rootcause.foshol.review.api;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.util.CatalogueLocale;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdvisoryView(
        UUID advisoryId,
        UUID caseId,
        UUID diseaseId,
        String diseaseNameBn,
        String diseaseNameEn,
        boolean diseaseNameEnFallback,
        UUID officerId,
        String officerName,
        AdvisoryAction action,
        String officerNoteBn,
        int version,
        UUID supersedesId,
        List<RemedyRefView> remedies,
        Instant publishedAt) {

    public static AdvisoryView of(
            UUID advisoryId,
            UUID caseId,
            UUID diseaseId,
            String diseaseNameBn,
            String diseaseNameEn,
            UUID officerId,
            String officerName,
            AdvisoryAction action,
            String officerNoteBn,
            int version,
            UUID supersedesId,
            List<RemedyRefView> remedies,
            Instant publishedAt) {
        boolean fallback = CatalogueLocale.enFallback(diseaseNameEn);
        return new AdvisoryView(
                advisoryId,
                caseId,
                diseaseId,
                diseaseNameBn,
                CatalogueLocale.enOrBn(diseaseNameEn, diseaseNameBn),
                fallback,
                officerId,
                officerName,
                action,
                officerNoteBn,
                version,
                supersedesId,
                remedies,
                publishedAt);
    }
}
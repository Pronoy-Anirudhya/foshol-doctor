package com.rootcause.foshol.review.api;

import com.rootcause.foshol.common.enums.RemedyRateBasis;
import com.rootcause.foshol.common.enums.RemedyRateUnit;
import com.rootcause.foshol.common.enums.RemedyType;
import com.rootcause.foshol.common.util.CatalogueLocale;
import com.rootcause.foshol.knowledge.api.RemedyView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RemedyRefView(
        UUID remedyId,
        RemedyType type,
        String titleBn,
        List<String> stepsBn,
        String dosageBn,
        Integer phiDays,
        String sourceRef,
        BigDecimal rateAmount,
        RemedyRateUnit rateUnit,
        RemedyRateBasis rateBasis,
        String rateNotesBn,
        ComputedDoseView computedDose,
        String titleEn,
        boolean titleEnFallback,
        List<String> stepsEn,
        boolean stepsEnFallback,
        String dosageEn,
        boolean dosageEnFallback,
        String rateNotesEn,
        boolean rateNotesEnFallback) {

    public static RemedyRefView from(RemedyView remedy, ComputedDoseView computed) {
        List<String> stepsBn = remedy.stepsBn() == null ? List.of() : remedy.stepsBn();
        List<String> stepsEnRaw = remedy.stepsEn();
        boolean titleFallback = CatalogueLocale.enFallback(remedy.titleEn());
        boolean stepsFallback = CatalogueLocale.listFallback(stepsEnRaw);
        boolean dosageFallback = CatalogueLocale.enFallback(remedy.dosageEn(), remedy.dosageBn());
        boolean notesFallback = CatalogueLocale.enFallback(remedy.rateNotesEn(), remedy.rateNotesBn());
        return new RemedyRefView(
                remedy.id(),
                remedy.type(),
                remedy.titleBn(),
                stepsBn,
                remedy.dosageBn(),
                remedy.phiDays(),
                remedy.sourceRef(),
                remedy.rateAmount(),
                remedy.rateUnit(),
                remedy.rateBasis(),
                remedy.rateNotesBn(),
                computed,
                CatalogueLocale.enOrBn(remedy.titleEn(), remedy.titleBn()),
                titleFallback,
                CatalogueLocale.enOrBn(stepsEnRaw, stepsBn),
                stepsFallback,
                dosageFallback ? remedy.dosageBn() : remedy.dosageEn(),
                dosageFallback,
                notesFallback ? remedy.rateNotesBn() : remedy.rateNotesEn(),
                notesFallback);
    }

    public static RemedyRefView missing(UUID remedyId) {
        return new RemedyRefView(
                remedyId,
                null,
                "",
                List.of(),
                null,
                null,
                "",
                null,
                null,
                null,
                null,
                null,
                "",
                true,
                List.of(),
                true,
                null,
                false,
                null,
                false);
    }
}
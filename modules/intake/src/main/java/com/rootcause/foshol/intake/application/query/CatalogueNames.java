package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.util.CatalogueLocale;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;

public record CatalogueNames(String bn, String en, boolean fallback) {

    public static CatalogueNames crop(CropView crop, String storedBn) {
        String catalogueBn = crop == null ? null : crop.nameBn();
        String catalogueEn = crop == null ? null : crop.nameEn();
        String bn = CatalogueLocale.isBlank(storedBn) ? (catalogueBn == null ? "" : catalogueBn) : storedBn;
        return new CatalogueNames(bn, CatalogueLocale.enOrBn(catalogueEn, bn), CatalogueLocale.enFallback(catalogueEn));
    }

    public static CatalogueNames disease(DiseaseView disease, String storedBn) {
        String catalogueBn = disease == null ? null : disease.nameBn();
        String catalogueEn = disease == null ? null : disease.nameEn();
        String bn = CatalogueLocale.isBlank(storedBn) ? catalogueBn : storedBn;
        return new CatalogueNames(bn, CatalogueLocale.enOrBn(catalogueEn, bn), CatalogueLocale.enFallback(catalogueEn));
    }
}

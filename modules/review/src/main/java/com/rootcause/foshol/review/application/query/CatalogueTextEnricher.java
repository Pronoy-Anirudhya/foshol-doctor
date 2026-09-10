package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.util.CatalogueLocale;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CatalogueTextEnricher {

    private final KnowledgeQueryApi knowledge;
    private final Map<String, CropView> cropsByCode;

    public CatalogueTextEnricher(KnowledgeQueryApi knowledge) {
        this.knowledge = knowledge;
        Map<String, CropView> byCode = new LinkedHashMap<>();
        for (CropView crop : knowledge.listCrops()) {
            byCode.putIfAbsent(crop.code(), crop);
        }
        this.cropsByCode = Map.copyOf(byCode);
    }

    public OfficerQueueRow enrich(OfficerQueueRow row) {
        Named crop = crop(row.cropCode(), row.cropNameBn());
        Named disease = disease(row.topDiseaseId(), row.topDiseaseNameBn());
        return new OfficerQueueRow(
                row.caseId(),
                row.reviewTaskId(),
                row.farmerName(),
                row.cropCode(),
                crop.bn(),
                crop.en(),
                crop.fallback(),
                row.districtCode(),
                row.decisionPath(),
                row.topDiseaseId(),
                disease.bn(),
                disease.en(),
                disease.fallback(),
                row.topConfidence(),
                row.imageCount(),
                row.hasAudio(),
                row.analysisMode(),
                row.state(),
                row.officerId(),
                row.isResubmission(),
                row.requeueCount(),
                row.submittedAt(),
                row.slaDueAt(),
                row.assignmentDueAt(),
                row.resolutionDueAt());
    }

    public Named crop(String cropCode, String nameBn) {
        CropView found = null;
        if (cropCode != null && !cropCode.isBlank()) {
            found = cropsByCode.get(cropCode);
            if (found == null) {
                found = knowledge.findCropByCode(cropCode).orElse(null);
            }
        }
        String en = found == null ? null : found.nameEn();
        return new Named(nameBn, CatalogueLocale.enOrBn(en, nameBn), CatalogueLocale.enFallback(en));
    }

    public Named disease(UUID diseaseId, String nameBn) {
        DiseaseView found = diseaseId == null ? null : knowledge.findDiseaseById(diseaseId).orElse(null);
        String bn = CatalogueLocale.isBlank(nameBn) && found != null ? found.nameBn() : nameBn;
        String en = found == null ? null : found.nameEn();
        return new Named(bn, CatalogueLocale.enOrBn(en, bn), CatalogueLocale.enFallback(en));
    }

    public record Named(String bn, String en, boolean fallback) {}
}
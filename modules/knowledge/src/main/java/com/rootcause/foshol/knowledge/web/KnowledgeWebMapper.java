package com.rootcause.foshol.knowledge.web;

import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.RemedyReadModel;
import com.rootcause.foshol.knowledge.application.query.SymptomReadModel;
import java.util.List;

public final class KnowledgeWebMapper {

    private KnowledgeWebMapper() {}

    public static CropResponse toCropResponse(CropReadModel row) {
        boolean fallback = isBlank(row.nameEn());
        return new CropResponse(
                row.id(),
                row.code(),
                row.nameBn(),
                fallback ? row.nameBn() : row.nameEn(),
                fallback,
                row.iconKey(),
                row.displayOrder());
    }

    public static DiseaseResponse toDiseaseResponse(DiseaseReadModel row) {
        boolean nameFallback = isBlank(row.nameEn());
        boolean descriptionFallback = isBlank(row.descriptionEn()) && !isBlank(row.descriptionBn());
        return new DiseaseResponse(
                row.id(),
                row.cropId(),
                row.code(),
                row.nameBn(),
                nameFallback ? row.nameBn() : row.nameEn(),
                nameFallback,
                row.descriptionBn(),
                descriptionFallback ? row.descriptionBn() : row.descriptionEn(),
                descriptionFallback,
                row.severity(),
                row.healthy());
    }

    public static RemedyResponse toRemedyResponse(RemedyReadModel row) {
        List<String> stepsBn = row.stepsBn() == null ? List.of() : row.stepsBn();
        boolean titleFallback = isBlank(row.titleEn());
        boolean stepsFallback = isBlankList(row.stepsEn());
        boolean dosageFallback = isBlank(row.dosageEn()) && !isBlank(row.dosageBn());
        boolean notesFallback = isBlank(row.rateNotesEn()) && !isBlank(row.rateNotesBn());
        return new RemedyResponse(
                row.id(),
                row.diseaseId(),
                row.type(),
                row.titleBn(),
                stepsBn,
                row.dosageBn(),
                row.phiDays(),
                row.costTier(),
                row.efficacy(),
                row.sourceRef(),
                row.displayOrder(),
                row.rateAmount(),
                row.rateUnit(),
                row.rateBasis(),
                row.rateNotesBn(),
                titleFallback ? row.titleBn() : row.titleEn(),
                titleFallback,
                stepsFallback ? stepsBn : row.stepsEn(),
                stepsFallback,
                dosageFallback ? row.dosageBn() : row.dosageEn(),
                dosageFallback,
                notesFallback ? row.rateNotesBn() : row.rateNotesEn(),
                notesFallback);
    }

    public static SymptomResponse toSymptomResponse(SymptomReadModel row) {
        boolean fallback = isBlank(row.nameEn());
        return new SymptomResponse(
                row.id(),
                row.code(),
                row.nameBn(),
                fallback ? row.nameBn() : row.nameEn(),
                fallback,
                row.organ());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isBlankList(List<String> values) {
        return values == null || values.isEmpty();
    }
}

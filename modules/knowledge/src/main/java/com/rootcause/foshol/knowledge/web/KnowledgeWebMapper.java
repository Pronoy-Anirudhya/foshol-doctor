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
        boolean fallback = isBlank(row.nameEn());
        return new DiseaseResponse(
                row.id(),
                row.cropId(),
                row.code(),
                row.nameBn(),
                fallback ? row.nameBn() : row.nameEn(),
                fallback,
                row.descriptionBn(),
                row.severity(),
                row.healthy());
    }

    public static RemedyResponse toRemedyResponse(RemedyReadModel row) {
        List<String> steps = row.stepsBn() == null ? List.of() : row.stepsBn();
        return new RemedyResponse(
                row.id(),
                row.diseaseId(),
                row.type(),
                row.titleBn(),
                steps,
                row.dosageBn(),
                row.phiDays(),
                row.costTier(),
                row.efficacy(),
                row.sourceRef(),
                row.displayOrder());
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
}

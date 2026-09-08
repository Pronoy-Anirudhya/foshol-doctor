package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import java.util.List;

public final class KnowledgeViewMapper {

    private KnowledgeViewMapper() {}

    public static CropView toCropView(CropReadModel row) {
        return new CropView(row.id(), row.code(), row.nameBn(), row.nameEn(), row.iconKey());
    }

    public static DiseaseView toDiseaseView(DiseaseReadModel row) {
        return new DiseaseView(
                row.id(),
                row.cropId(),
                row.code(),
                row.nameBn(),
                row.nameEn(),
                row.descriptionBn(),
                row.severity(),
                row.healthy());
    }

    public static RemedyView toRemedyView(RemedyReadModel row) {
        List<String> steps = row.stepsBn() == null ? List.of() : List.copyOf(row.stepsBn());
        return new RemedyView(
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
                row.rateAmount(),
                row.rateUnit(),
                row.rateBasis(),
                row.rateNotesBn());
    }

    public static SymptomRefView toSymptomRefView(SymptomReadModel row) {
        return new SymptomRefView(row.id(), row.code(), row.nameBn(), row.nameEn(), row.organ());
    }
}

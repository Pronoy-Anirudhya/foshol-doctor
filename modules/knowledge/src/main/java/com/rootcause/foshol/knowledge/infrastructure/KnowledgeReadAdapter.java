package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.RemedyReadModel;
import com.rootcause.foshol.knowledge.application.query.SymptomReadModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KnowledgeReadAdapter implements KnowledgeReadPort {

    private final CropJpaRepository crops;
    private final DiseaseJpaRepository diseases;
    private final RemedyJpaRepository remedies;
    private final SymptomJpaRepository symptoms;

    public KnowledgeReadAdapter(
            CropJpaRepository crops,
            DiseaseJpaRepository diseases,
            RemedyJpaRepository remedies,
            SymptomJpaRepository symptoms) {
        this.crops = crops;
        this.diseases = diseases;
        this.remedies = remedies;
        this.symptoms = symptoms;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CropReadModel> listCrops() {
        return crops.findLiveCrops().stream().map(KnowledgeReadAdapter::toCrop).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CropReadModel> findCropById(UUID cropId) {
        if (cropId == null) {
            return Optional.empty();
        }
        return crops.findLiveById(cropId).map(KnowledgeReadAdapter::toCrop);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CropReadModel> findCropByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return crops.findLiveByCode(code).map(KnowledgeReadAdapter::toCrop);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiseaseReadModel> findDiseaseById(UUID diseaseId) {
        if (diseaseId == null) {
            return Optional.empty();
        }
        return diseases.findLiveById(diseaseId).map(KnowledgeReadAdapter::toDisease);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiseaseReadModel> listDiseasesByCrop(UUID cropId) {
        if (cropId == null) {
            return List.of();
        }
        return diseases.findLiveByCropId(cropId).stream().map(KnowledgeReadAdapter::toDisease).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RemedyReadModel> listActiveRemedies(UUID diseaseId) {
        if (diseaseId == null) {
            return List.of();
        }
        List<RemedyReadModel> mapped = new ArrayList<>();
        for (RemedyReadRow row : remedies.findActiveByDiseaseId(diseaseId)) {
            mapped.add(toRemedy(row));
        }
        return List.copyOf(mapped);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SymptomReadModel> listSymptoms() {
        return symptoms.findLiveSymptoms().stream().map(KnowledgeReadAdapter::toSymptom).toList();
    }

    private static CropReadModel toCrop(CropReadRow row) {
        int displayOrder = row.getDisplayOrder() == null ? 0 : row.getDisplayOrder();
        return new CropReadModel(
                row.getId(), row.getCode(), row.getNameBn(), row.getNameEn(), row.getIconKey(), displayOrder);
    }

    private static DiseaseReadModel toDisease(DiseaseReadRow row) {
        return new DiseaseReadModel(
                row.getId(),
                row.getCropId(),
                row.getCode(),
                row.getNameBn(),
                row.getNameEn(),
                row.getDescriptionBn(),
                row.getSeverity(),
                row.getHealthy());
    }

    private static RemedyReadModel toRemedy(RemedyReadRow row) {
        Integer phiDays = row.getPhiDays() == null ? null : row.getPhiDays().intValue();
        int displayOrder = row.getDisplayOrder() == null ? 0 : row.getDisplayOrder();
        return new RemedyReadModel(
                row.getId(),
                row.getDiseaseId(),
                row.getType(),
                row.getTitleBn(),
                RemedyStepsParser.parse(row.getStepsBn()),
                row.getDosageBn(),
                phiDays,
                row.getCostTier(),
                row.getEfficacy(),
                row.getSourceRef(),
                displayOrder,
                row.getRateAmount(),
                row.getRateUnit(),
                row.getRateBasis(),
                row.getRateNotesBn());
    }

    private static SymptomReadModel toSymptom(SymptomReadRow row) {
        return new SymptomReadModel(
                row.getId(), row.getCode(), row.getNameBn(), row.getNameEn(), row.getOrgan());
    }
}

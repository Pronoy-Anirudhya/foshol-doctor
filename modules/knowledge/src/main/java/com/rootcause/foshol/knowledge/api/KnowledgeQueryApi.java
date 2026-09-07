package com.rootcause.foshol.knowledge.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeQueryApi {

    List<CropView> listCrops();

    Optional<CropView> findCropById(UUID cropId);

    Optional<CropView> findCropByCode(String code);

    Optional<DiseaseView> findDiseaseById(UUID diseaseId);

    List<DiseaseView> listDiseasesByCrop(UUID cropId);

    List<RemedyView> listActiveRemedies(UUID diseaseId);

    List<SymptomRefView> listSymptoms();

    Optional<UUID> resolveModelLabel(String modelId, String modelVersion, String rawLabel);
}

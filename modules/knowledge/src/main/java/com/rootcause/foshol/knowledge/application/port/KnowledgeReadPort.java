package com.rootcause.foshol.knowledge.application.port;

import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.RemedyReadModel;
import com.rootcause.foshol.knowledge.application.query.SymptomReadModel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeReadPort {

    List<CropReadModel> listCrops();

    Optional<CropReadModel> findCropById(UUID cropId);

    Optional<CropReadModel> findCropByCode(String code);

    Optional<DiseaseReadModel> findDiseaseById(UUID diseaseId);

    List<DiseaseReadModel> listDiseasesByCrop(UUID cropId);

    List<RemedyReadModel> listActiveRemedies(UUID diseaseId);

    List<SymptomReadModel> listSymptoms();
}

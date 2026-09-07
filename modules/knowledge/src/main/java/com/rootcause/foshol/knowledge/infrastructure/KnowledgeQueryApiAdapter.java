package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import com.rootcause.foshol.knowledge.application.query.FindCropByCodeQuery;
import com.rootcause.foshol.knowledge.application.query.FindCropByCodeQueryHandler;
import com.rootcause.foshol.knowledge.application.query.FindCropByIdQuery;
import com.rootcause.foshol.knowledge.application.query.FindCropByIdQueryHandler;
import com.rootcause.foshol.knowledge.application.query.FindDiseaseByIdQuery;
import com.rootcause.foshol.knowledge.application.query.FindDiseaseByIdQueryHandler;
import com.rootcause.foshol.knowledge.application.query.KnowledgeViewMapper;
import com.rootcause.foshol.knowledge.application.query.ListActiveRemediesQuery;
import com.rootcause.foshol.knowledge.application.query.ListActiveRemediesQueryHandler;
import com.rootcause.foshol.knowledge.application.query.ListCropsQuery;
import com.rootcause.foshol.knowledge.application.query.ListCropsQueryHandler;
import com.rootcause.foshol.knowledge.application.query.ListDiseasesByCropQuery;
import com.rootcause.foshol.knowledge.application.query.ListDiseasesByCropQueryHandler;
import com.rootcause.foshol.knowledge.application.query.ListSymptomsQuery;
import com.rootcause.foshol.knowledge.application.query.ListSymptomsQueryHandler;
import com.rootcause.foshol.knowledge.application.query.ResolveModelLabelQuery;
import com.rootcause.foshol.knowledge.application.query.ResolveModelLabelQueryHandler;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeQueryApiAdapter implements KnowledgeQueryApi {

    private final ListCropsQueryHandler listCrops;
    private final FindCropByIdQueryHandler findCropById;
    private final FindCropByCodeQueryHandler findCropByCode;
    private final FindDiseaseByIdQueryHandler findDiseaseById;
    private final ListDiseasesByCropQueryHandler listDiseasesByCrop;
    private final ListActiveRemediesQueryHandler listActiveRemedies;
    private final ListSymptomsQueryHandler listSymptoms;
    private final ResolveModelLabelQueryHandler resolveModelLabel;

    public KnowledgeQueryApiAdapter(
            ListCropsQueryHandler listCrops,
            FindCropByIdQueryHandler findCropById,
            FindCropByCodeQueryHandler findCropByCode,
            FindDiseaseByIdQueryHandler findDiseaseById,
            ListDiseasesByCropQueryHandler listDiseasesByCrop,
            ListActiveRemediesQueryHandler listActiveRemedies,
            ListSymptomsQueryHandler listSymptoms,
            ResolveModelLabelQueryHandler resolveModelLabel) {
        this.listCrops = listCrops;
        this.findCropById = findCropById;
        this.findCropByCode = findCropByCode;
        this.findDiseaseById = findDiseaseById;
        this.listDiseasesByCrop = listDiseasesByCrop;
        this.listActiveRemedies = listActiveRemedies;
        this.listSymptoms = listSymptoms;
        this.resolveModelLabel = resolveModelLabel;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CropView> listCrops() {
        return listCrops.handle(new ListCropsQuery()).stream()
                .map(KnowledgeViewMapper::toCropView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CropView> findCropById(UUID cropId) {
        return findCropById.handle(new FindCropByIdQuery(cropId)).map(KnowledgeViewMapper::toCropView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CropView> findCropByCode(String code) {
        return findCropByCode.handle(new FindCropByCodeQuery(code)).map(KnowledgeViewMapper::toCropView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiseaseView> findDiseaseById(UUID diseaseId) {
        return findDiseaseById
                .handle(new FindDiseaseByIdQuery(diseaseId))
                .map(KnowledgeViewMapper::toDiseaseView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiseaseView> listDiseasesByCrop(UUID cropId) {
        return listDiseasesByCrop.handle(new ListDiseasesByCropQuery(cropId)).stream()
                .map(KnowledgeViewMapper::toDiseaseView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RemedyView> listActiveRemedies(UUID diseaseId) {
        return listActiveRemedies.handle(new ListActiveRemediesQuery(diseaseId)).stream()
                .map(KnowledgeViewMapper::toRemedyView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SymptomRefView> listSymptoms() {
        return listSymptoms.handle(new ListSymptomsQuery()).stream()
                .map(KnowledgeViewMapper::toSymptomRefView)
                .toList();
    }

    @Override
    public Optional<UUID> resolveModelLabel(String modelId, String modelVersion, String rawLabel) {
        return resolveModelLabel.handle(new ResolveModelLabelQuery(modelId, modelVersion, rawLabel));
    }
}

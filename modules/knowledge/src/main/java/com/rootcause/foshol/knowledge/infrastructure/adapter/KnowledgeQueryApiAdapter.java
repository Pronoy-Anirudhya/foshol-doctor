package com.rootcause.foshol.knowledge.infrastructure.adapter;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.FindCropByCodeQuery;
import com.rootcause.foshol.knowledge.application.query.FindCropByIdQuery;
import com.rootcause.foshol.knowledge.application.query.FindDiseaseByIdQuery;
import com.rootcause.foshol.knowledge.application.query.KnowledgeViewMapper;
import com.rootcause.foshol.knowledge.application.query.ListActiveRemediesQuery;
import com.rootcause.foshol.knowledge.application.query.ListCropsQuery;
import com.rootcause.foshol.knowledge.application.query.ListDiseasesByCropQuery;
import com.rootcause.foshol.knowledge.application.query.ListSymptomsQuery;
import com.rootcause.foshol.knowledge.application.query.RemedyReadModel;
import com.rootcause.foshol.knowledge.application.query.ResolveModelLabelQuery;
import com.rootcause.foshol.knowledge.application.query.SymptomReadModel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeQueryApiAdapter implements KnowledgeQueryApi {

    private final QueryBus queries;

    public KnowledgeQueryApiAdapter(QueryBus queries) {
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CropView> listCrops() {
        List<CropReadModel> rows = queries.handle(new ListCropsQuery());
        return rows.stream().map(KnowledgeViewMapper::toCropView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CropView> findCropById(UUID cropId) {
        Optional<CropReadModel> row = queries.handle(new FindCropByIdQuery(cropId));
        return row.map(KnowledgeViewMapper::toCropView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CropView> findCropByCode(String code) {
        Optional<CropReadModel> row = queries.handle(new FindCropByCodeQuery(code));
        return row.map(KnowledgeViewMapper::toCropView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiseaseView> findDiseaseById(UUID diseaseId) {
        Optional<DiseaseReadModel> row = queries.handle(new FindDiseaseByIdQuery(diseaseId));
        return row.map(KnowledgeViewMapper::toDiseaseView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiseaseView> listDiseasesByCrop(UUID cropId) {
        List<DiseaseReadModel> rows = queries.handle(new ListDiseasesByCropQuery(cropId));
        return rows.stream().map(KnowledgeViewMapper::toDiseaseView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RemedyView> listActiveRemedies(UUID diseaseId) {
        List<RemedyReadModel> rows = queries.handle(new ListActiveRemediesQuery(diseaseId));
        return rows.stream().map(KnowledgeViewMapper::toRemedyView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SymptomRefView> listSymptoms() {
        List<SymptomReadModel> rows = queries.handle(new ListSymptomsQuery());
        return rows.stream().map(KnowledgeViewMapper::toSymptomRefView).toList();
    }

    @Override
    public Optional<UUID> resolveModelLabel(String modelId, String modelVersion, String rawLabel) {
        return queries.handle(new ResolveModelLabelQuery(modelId, modelVersion, rawLabel));
    }
}

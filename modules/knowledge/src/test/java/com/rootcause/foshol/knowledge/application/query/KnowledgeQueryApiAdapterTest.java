package com.rootcause.foshol.knowledge.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.RemedyType;
import com.rootcause.foshol.common.enums.Severity;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.knowledge.infrastructure.adapter.KnowledgeQueryApiAdapter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeQueryApiAdapterTest {

    private static final UUID RICE = UUID.fromString("01800000-0000-7000-8000-000000000001");
    private static final UUID BLAST = UUID.fromString("01800000-0000-7000-8000-000000000103");
    private static final UUID REMEDY = UUID.fromString("01800000-0000-7000-8000-000000000701");
    private static final UUID SYMPTOM = UUID.fromString("01800000-0000-7000-8000-000000000501");

    private QueryHandler<ListCropsQuery, List<CropReadModel>> listCrops;
    private QueryHandler<FindCropByIdQuery, Optional<CropReadModel>> findCropById;
    private QueryHandler<FindCropByCodeQuery, Optional<CropReadModel>> findCropByCode;
    private QueryHandler<FindDiseaseByIdQuery, Optional<DiseaseReadModel>> findDiseaseById;
    private QueryHandler<ListDiseasesByCropQuery, List<DiseaseReadModel>> listDiseasesByCrop;
    private QueryHandler<ListActiveRemediesQuery, List<RemedyReadModel>> listActiveRemedies;
    private QueryHandler<ListSymptomsQuery, List<SymptomReadModel>> listSymptoms;
    private QueryHandler<ResolveModelLabelQuery, Optional<UUID>> resolveModelLabel;
    private KnowledgeQueryApi api;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        listCrops = mock(QueryHandler.class);
        findCropById = mock(QueryHandler.class);
        findCropByCode = mock(QueryHandler.class);
        findDiseaseById = mock(QueryHandler.class);
        listDiseasesByCrop = mock(QueryHandler.class);
        listActiveRemedies = mock(QueryHandler.class);
        listSymptoms = mock(QueryHandler.class);
        resolveModelLabel = mock(QueryHandler.class);
        QueryBus queries = new QueryBus();
        queries.register(ListCropsQuery.class, listCrops);
        queries.register(FindCropByIdQuery.class, findCropById);
        queries.register(FindCropByCodeQuery.class, findCropByCode);
        queries.register(FindDiseaseByIdQuery.class, findDiseaseById);
        queries.register(ListDiseasesByCropQuery.class, listDiseasesByCrop);
        queries.register(ListActiveRemediesQuery.class, listActiveRemedies);
        queries.register(ListSymptomsQuery.class, listSymptoms);
        queries.register(ResolveModelLabelQuery.class, resolveModelLabel);
        api = new KnowledgeQueryApiAdapter(queries);
    }

    @Test
    void mapsCropReadModelToViewDroppingDisplayOrder() {
        when(listCrops.handle(new ListCropsQuery()))
                .thenReturn(List.of(new CropReadModel(RICE, "rice", "ধান", "Rice", "crop-rice", 1)));
        List<CropView> crops = api.listCrops();
        assertThat(crops).containsExactly(new CropView(RICE, "rice", "ধান", "Rice", "crop-rice"));
    }

    @Test
    void mapsNullEnglishVerbatimOnApiView() {
        when(findCropById.handle(new FindCropByIdQuery(RICE)))
                .thenReturn(Optional.of(new CropReadModel(RICE, "rice", "ধান", null, "crop-rice", 1)));
        assertThat(api.findCropById(RICE)).contains(new CropView(RICE, "rice", "ধান", null, "crop-rice"));
    }

    @Test
    void mapsRemedyRateColumnsOntoApiView() {
        when(listActiveRemedies.handle(new ListActiveRemediesQuery(BLAST)))
                .thenReturn(List.of(new RemedyReadModel(
                        REMEDY,
                        BLAST,
                        RemedyType.CHEMICAL,
                        "title",
                        List.of("step"),
                        "dosage",
                        14,
                        "LOW",
                        "HIGH",
                        "source",
                        1,
                        new java.math.BigDecimal("50"),
                        com.rootcause.foshol.common.enums.RemedyRateUnit.ML,
                        com.rootcause.foshol.common.enums.RemedyRateBasis.PER_DECIMAL,
                        "note")));
        RemedyView remedy = api.listActiveRemedies(BLAST).getFirst();
        assertThat(remedy.rateAmount()).isEqualByComparingTo("50");
        assertThat(remedy.rateUnit()).isEqualTo(com.rootcause.foshol.common.enums.RemedyRateUnit.ML);
        assertThat(remedy.rateBasis()).isEqualTo(com.rootcause.foshol.common.enums.RemedyRateBasis.PER_DECIMAL);
        assertThat(remedy.rateNotesBn()).isEqualTo("note");
    }

    @Test
    void mapsDiseaseAndNeverNullRemedySteps() {
        when(findDiseaseById.handle(new FindDiseaseByIdQuery(BLAST)))
                .thenReturn(Optional.of(new DiseaseReadModel(
                        BLAST, RICE, "blast", "ব্লাস্ট", "Blast", null, Severity.LOW, false)));
        when(listActiveRemedies.handle(new ListActiveRemediesQuery(BLAST)))
                .thenReturn(List.of(new RemedyReadModel(
                        REMEDY,
                        BLAST,
                        RemedyType.CULTURAL,
                        "title",
                        null,
                        null,
                        null,
                        "LOW",
                        "LOW",
                        "source",
                        1,
                        null,
                        null,
                        null,
                        null)));
        DiseaseView disease = api.findDiseaseById(BLAST).orElseThrow();
        assertThat(disease.healthy()).isFalse();
        assertThat(disease.cropId()).isEqualTo(RICE);
        RemedyView remedy = api.listActiveRemedies(BLAST).getFirst();
        assertThat(remedy.stepsBn()).isEmpty();
    }

    @Test
    void nullIdentifiersReturnEmptyWithoutThrowing() {
        when(findCropById.handle(new FindCropByIdQuery(null))).thenReturn(Optional.empty());
        when(findDiseaseById.handle(new FindDiseaseByIdQuery(null))).thenReturn(Optional.empty());
        when(listDiseasesByCrop.handle(new ListDiseasesByCropQuery(null))).thenReturn(List.of());
        when(listActiveRemedies.handle(new ListActiveRemediesQuery(null))).thenReturn(List.of());
        when(resolveModelLabel.handle(new ResolveModelLabelQuery(null, null, null)))
                .thenReturn(Optional.empty());
        assertThat(api.findCropById(null)).isEmpty();
        assertThat(api.findDiseaseById(null)).isEmpty();
        assertThat(api.listDiseasesByCrop(null)).isEmpty();
        assertThat(api.listActiveRemedies(null)).isEmpty();
        assertThat(api.resolveModelLabel(null, null, null)).isEmpty();
    }

    @Test
    void resolveModelLabelIsCaseSensitiveEmptyWhenIndexMisses() {
        when(resolveModelLabel.handle(new ResolveModelLabelQuery("m", "1", "leaf_blast")))
                .thenReturn(Optional.empty());
        assertThat(api.resolveModelLabel("m", "1", "leaf_blast")).isEmpty();
    }

    @Test
    void mapsSymptomWithoutEmbedding() {
        when(listSymptoms.handle(new ListSymptomsQuery()))
                .thenReturn(List.of(new SymptomReadModel(SYMPTOM, "yellow_leaf", "হলুদ", "Yellow", "LEAF")));
        assertThat(api.listSymptoms().getFirst().code()).isEqualTo("yellow_leaf");
    }
}

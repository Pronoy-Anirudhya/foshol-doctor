package com.rootcause.foshol.knowledge.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.Severity;
import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.port.ModelLabelIndex;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.FindCropByCodeQuery;
import com.rootcause.foshol.knowledge.application.query.FindCropByIdQuery;
import com.rootcause.foshol.knowledge.application.query.FindDiseaseByIdQuery;
import com.rootcause.foshol.knowledge.application.query.ListActiveRemediesQuery;
import com.rootcause.foshol.knowledge.application.query.ListCropsQuery;
import com.rootcause.foshol.knowledge.application.query.ListDiseasesByCropQuery;
import com.rootcause.foshol.knowledge.application.query.ListSymptomsQuery;
import com.rootcause.foshol.knowledge.application.query.ResolveModelLabelQuery;
import com.rootcause.foshol.knowledge.application.query.SymptomReadModel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class KnowledgeQueryHandlersTest {

    @Mock
    private KnowledgeReadPort reads;

    @Mock
    private ModelLabelIndex labels;

    @Test
    void listCropsDelegates() {
        CropReadModel crop = new CropReadModel(UUID.randomUUID(), "rice", "bn", "en", "icon", 1);
        when(reads.listCrops()).thenReturn(List.of(crop));
        assertThat(new ListCropsQueryHandler(reads).handle(new ListCropsQuery())).containsExactly(crop);
    }

    @Test
    void findCropByIdEmptyForNull() {
        assertThat(new FindCropByIdQueryHandler(reads).handle(new FindCropByIdQuery(null))).isEmpty();
        verifyNoInteractions(reads);
    }

    @Test
    void findCropByCodeEmptyForUnknown() {
        when(reads.findCropByCode("nope")).thenReturn(Optional.empty());
        assertThat(new FindCropByCodeQueryHandler(reads).handle(new FindCropByCodeQuery("nope"))).isEmpty();
    }

    @Test
    void findDiseaseByIdEmptyForUnknown() {
        UUID id = UUID.randomUUID();
        when(reads.findDiseaseById(id)).thenReturn(Optional.empty());
        assertThat(new FindDiseaseByIdQueryHandler(reads).handle(new FindDiseaseByIdQuery(id))).isEmpty();
    }

    @Test
    void listDiseasesReturnsHealthyLastFromPort() {
        UUID cropId = UUID.randomUUID();
        DiseaseReadModel sick = disease(cropId, "blast", false);
        DiseaseReadModel healthy = disease(cropId, "healthy", true);
        when(reads.listDiseasesByCrop(cropId)).thenReturn(List.of(sick, healthy));
        assertThat(new ListDiseasesByCropQueryHandler(reads).handle(new ListDiseasesByCropQuery(cropId)))
                .containsExactly(sick, healthy);
    }

    @Test
    void listActiveRemediesEmptyForNull() {
        assertThat(new ListActiveRemediesQueryHandler(reads).handle(new ListActiveRemediesQuery(null)))
                .isEmpty();
        verifyNoInteractions(reads);
    }

    @Test
    void listSymptomsDelegates() {
        SymptomReadModel view = new SymptomReadModel(UUID.randomUUID(), "s", "bn", "en", "LEAF");
        when(reads.listSymptoms()).thenReturn(List.of(view));
        assertThat(new ListSymptomsQueryHandler(reads).handle(new ListSymptomsQuery())).containsExactly(view);
    }

    @Test
    void resolveModelLabelIsCaseSensitive() {
        when(labels.resolve("m", "1", "Leaf_Blast")).thenReturn(Optional.of(UUID.randomUUID()));
        when(labels.resolve("m", "1", "leaf_blast")).thenReturn(Optional.empty());
        ResolveModelLabelQueryHandler handler = new ResolveModelLabelQueryHandler(labels);
        assertThat(handler.handle(new ResolveModelLabelQuery("m", "1", "Leaf_Blast"))).isPresent();
        assertThat(handler.handle(new ResolveModelLabelQuery("m", "1", "leaf_blast"))).isEmpty();
    }

    @Test
    void resolveModelLabelEmptyForNull() {
        assertThat(new ResolveModelLabelQueryHandler(labels).handle(new ResolveModelLabelQuery(null, "1", "x")))
                .isEmpty();
        verifyNoInteractions(labels);
    }

    private static DiseaseReadModel disease(UUID cropId, String code, boolean healthy) {
        return new DiseaseReadModel(
                UUID.randomUUID(), cropId, code, code, code, null, Severity.LOW, healthy);
    }
}

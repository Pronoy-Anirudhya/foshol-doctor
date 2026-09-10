package com.rootcause.foshol.review.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.ReviewState;
import com.rootcause.foshol.common.enums.Severity;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogueTextEnricherTest {

    @Mock
    private KnowledgeQueryApi knowledge;

    @Test
    void blankEnglishFallsBackToBangla() {
        UUID cropId = Uuid7.create();
        UUID diseaseId = Uuid7.create();
        when(knowledge.listCrops()).thenReturn(List.of(new CropView(cropId, "rice", "ধান", "  ", null)));
        when(knowledge.findDiseaseById(diseaseId))
                .thenReturn(Optional.of(new DiseaseView(
                        diseaseId, cropId, "blast", "ব্লাস্ট", null, null, Severity.HIGH, false)));
        CatalogueTextEnricher enricher = new CatalogueTextEnricher(knowledge);
        OfficerQueueRow row = enricher.enrich(row("ধান", diseaseId, "ব্লাস্ট"));
        assertThat(row.cropNameEn()).isEqualTo("ধান");
        assertThat(row.cropNameEnFallback()).isTrue();
        assertThat(row.topDiseaseNameEn()).isEqualTo("ব্লাস্ট");
        assertThat(row.topDiseaseNameEnFallback()).isTrue();
    }

    @Test
    void usesCatalogueEnglishWhenPresent() {
        UUID cropId = Uuid7.create();
        UUID diseaseId = Uuid7.create();
        when(knowledge.listCrops()).thenReturn(List.of(new CropView(cropId, "rice", "ধান", "Rice", null)));
        when(knowledge.findDiseaseById(diseaseId))
                .thenReturn(Optional.of(new DiseaseView(
                        diseaseId, cropId, "blast", "ব্লাস্ট", "Blast", null, Severity.HIGH, false)));
        CatalogueTextEnricher enricher = new CatalogueTextEnricher(knowledge);
        OfficerQueueRow row = enricher.enrich(row("ধান", diseaseId, "ব্লাস্ট"));
        assertThat(row.cropNameEn()).isEqualTo("Rice");
        assertThat(row.cropNameEnFallback()).isFalse();
        assertThat(row.topDiseaseNameEn()).isEqualTo("Blast");
        assertThat(row.topDiseaseNameEnFallback()).isFalse();
    }

    private static OfficerQueueRow row(String cropBn, UUID diseaseId, String diseaseBn) {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        return new OfficerQueueRow(
                Uuid7.create(),
                Uuid7.create(),
                "Farmer",
                "rice",
                cropBn,
                cropBn,
                true,
                "DHA",
                DecisionPath.PRIMARY,
                diseaseId,
                diseaseBn,
                diseaseBn,
                true,
                BigDecimal.ONE,
                1,
                false,
                AiMode.REPLAY,
                ReviewState.PENDING,
                null,
                false,
                (short) 0,
                t0,
                t0,
                null,
                null);
    }
}

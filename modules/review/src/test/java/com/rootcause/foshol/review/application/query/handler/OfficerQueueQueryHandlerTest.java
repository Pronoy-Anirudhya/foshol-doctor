package com.rootcause.foshol.review.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.ReviewState;
import com.rootcause.foshol.common.enums.Severity;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import com.rootcause.foshol.review.application.query.OfficerQueueRow;
import com.rootcause.foshol.review.domain.ReviewException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficerQueueQueryHandlerTest {

    @Mock
    private ReviewQueryPort reads;

    @Mock
    private OfficerLookupApi officers;

    @Mock
    private KnowledgeQueryApi knowledge;

    @Test
    void sortParameterIsRejected() {
        OfficerQueueQueryHandler handler = new OfficerQueueQueryHandler(reads, officers, knowledge);
        assertThatThrownBy(() -> handler.handle(new OfficerQueueQuery(
                        "PENDING", false, Uuid7.create(), "DHA", 0, 20, "confidence", "desc")))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_QUEUE_SORT_NOT_SUPPORTED);
        verifyNoInteractions(reads);
    }

    @Test
    void copiesEnglishCropAndDiseaseFromCatalogue() {
        UUID caseId = Uuid7.create();
        UUID taskId = Uuid7.create();
        UUID diseaseId = Uuid7.create();
        UUID cropId = Uuid7.create();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        OfficerQueueRow raw = new OfficerQueueRow(
                caseId,
                taskId,
                "Farmer",
                "rice",
                "ধান",
                "ধান",
                true,
                "DHA",
                DecisionPath.PRIMARY,
                diseaseId,
                "ব্লাস্ট",
                "ব্লাস্ট",
                true,
                new BigDecimal("0.91"),
                1,
                false,
                AiMode.REPLAY,
                ReviewState.PENDING,
                null,
                false,
                (short) 0,
                t0,
                t0.plus(Duration.ofHours(4)),
                null,
                null);
        when(reads.findQueue(any())).thenReturn(new OfficerQueuePage(List.of(raw), 0, 20, 1, 1));
        when(knowledge.listCrops()).thenReturn(List.of(new CropView(cropId, "rice", "ধান", "Rice", null)));
        when(knowledge.findDiseaseById(diseaseId))
                .thenReturn(Optional.of(new DiseaseView(
                        diseaseId, cropId, "blast", "ব্লাস্ট", "Blast", null, Severity.HIGH, false)));
        OfficerQueueQueryHandler handler = new OfficerQueueQueryHandler(reads, officers, knowledge);
        OfficerQueueRow row = handler
                .handle(new OfficerQueueQuery("PENDING", false, Uuid7.create(), "DHA", 0, 20, null, null))
                .content()
                .getFirst();
        assertThat(row.cropNameEn()).isEqualTo("Rice");
        assertThat(row.cropNameEnFallback()).isFalse();
        assertThat(row.topDiseaseNameEn()).isEqualTo("Blast");
        assertThat(row.topDiseaseNameEnFallback()).isFalse();
    }
}

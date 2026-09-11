package com.rootcause.foshol.review.web;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.enums.MetricsSource;
import com.rootcause.foshol.common.enums.ReviewState;
import com.rootcause.foshol.common.events.CaseImageRef;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailView;
import com.rootcause.foshol.review.web.ReviewCaseDetailResponse.CaseImageHttp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewCaseDetailResponseTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final BigDecimal HIGH = new BigDecimal("0.75");
    private static final BigDecimal LOW = new BigDecimal("0.45");
    private static final UUID IMAGE_ID = Uuid7.create();

    @Test
    void mapsOpenApiEnvelopeAndFlatOverlayFields() {
        UUID caseId = Uuid7.create();
        UUID taskId = Uuid7.create();
        String key = "cases/" + caseId + "/gradcam/" + IMAGE_ID + ".png";
        ReviewCaseDetailResponse body = ReviewCaseDetailResponse.from(view(caseId, taskId, key, true), HIGH, LOW);

        assertThat(body.task().taskId()).isEqualTo(taskId);
        assertThat(body.caseDetail().caseId()).isEqualTo(caseId);
        assertThat(body.caseDetail().images()).extracting(CaseImageHttp::imageId).containsExactly(IMAGE_ID);
        assertThat(body.analysis().hasGradcam()).isTrue();
        assertThat(body.analysis().mode()).isEqualTo(AiMode.REPLAY);
        assertThat(body.analysis().thresholds().high()).isEqualByComparingTo(HIGH);
        assertThat(body.analysis().thresholds().low()).isEqualByComparingTo(LOW);
        assertThat(body.detail().hasGradcam()).isTrue();
        assertThat(body.detail().gradcamObjectKey()).isEqualTo(key);
        assertThat(body.detail().farmerName()).isEqualTo("Farmer");
        assertThat(body.priorAdvisory()).isNull();
        assertThat(body.detail().suggestedDiseaseId()).isNull();

        JsonNode json = JsonMapper.builder().build().valueToTree(body);
        assertThat(json.has("detail")).isFalse();
        assertThat(json.has("caseDetail")).isFalse();
        assertThat(json.path("case").path("caseId").asText()).isEqualTo(caseId.toString());
        assertThat(json.path("analysis").path("hasGradcam").asBoolean()).isTrue();
        assertThat(json.path("hasGradcam").asBoolean()).isTrue();
        assertThat(json.path("gradcamObjectKey").asText()).isEqualTo(key);
        assertThat(json.path("task").path("taskId").asText()).isEqualTo(taskId.toString());
    }

    @Test
    void nestedHasGradcamIsFalseWhenNoOverlay() {
        ReviewCaseDetailResponse body =
                ReviewCaseDetailResponse.from(view(Uuid7.create(), Uuid7.create(), null, false), HIGH, LOW);
        assertThat(body.analysis().hasGradcam()).isFalse();
        assertThat(body.detail().hasGradcam()).isFalse();
        assertThat(body.detail().gradcamObjectKey()).isNull();
    }

    private static ReviewTaskDetailView view(UUID caseId, UUID taskId, String gradcamKey, boolean hasGradcam) {
        return new ReviewTaskDetailView(
                caseId,
                taskId,
                "Farmer",
                "rice",
                "ধান",
                "Rice",
                false,
                "DHK01",
                DecisionPath.PRIMARY,
                null,
                null,
                null,
                false,
                new BigDecimal("0.91"),
                1,
                false,
                AiMode.REPLAY,
                ReviewState.PENDING,
                null,
                false,
                (short) 0,
                T0,
                T0,
                null,
                null,
                new BigDecimal("0.91"),
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                gradcamKey,
                hasGradcam,
                List.of(new CaseImageRef(IMAGE_ID, "orig", "der", "abc", new BigDecimal("0.9"), true, 1)),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                0,
                Uuid7.create(),
                CaseStatus.IN_REVIEW,
                null,
                new BigDecimal("2"),
                FieldAreaUnit.DECIMAL,
                null,
                null,
                MetricsSource.FORM,
                List.of(),
                "replay",
                "v",
                12,
                null,
                null);
    }
}

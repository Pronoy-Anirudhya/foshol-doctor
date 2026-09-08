package com.rootcause.foshol.review.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.MetricsSource;
import com.rootcause.foshol.common.RemedyRateBasis;
import com.rootcause.foshol.common.RemedyRateUnit;
import com.rootcause.foshol.common.RemedyType;
import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.CandidateSource;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.review.api.RemedyRefView;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.port.ReviewQueryPort.QueueTaskRow;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailQuery;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailView;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class ReviewTaskDetailQueryHandlerTest {

    private static final UUID TASK = Uuid7.create();
    private static final UUID CASE = Uuid7.create();
    private static final UUID DISEASE = Uuid7.create();
    private static final UUID REMEDY = Uuid7.create();
    private static final UUID FARMER = Uuid7.create();
    private static final UUID CROP = Uuid7.create();
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static final UUID OFFICER_A = Uuid7.create();

    @Mock
    private ReviewQueryPort reads;

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private AdvisoryRepository advisories;

    @Mock
    private AnalysisApi analysisApi;

    @Mock
    private CaseIntakeApi cases;

    @Mock
    private KnowledgeQueryApi knowledge;

    @Mock
    private OfficerLookupApi officers;

    @Mock
    private com.rootcause.foshol.review.application.ReviewDistrictGuard districtGuard;

    @Test
    void missingTask() {
        when(reads.findQueueRow(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        ReviewTaskDetailQueryHandler handler = handler();
        assertThatThrownBy(() -> handler.handle(new ReviewTaskDetailQuery(Uuid7.create(), OFFICER_A)))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_REVIEW_TASK_NOT_FOUND);
    }

    @Test
    void suggestedRemediesIncludeComputedDoseWhenRatePresent() {
        QueueTaskRow row = new QueueTaskRow(
                CASE,
                TASK,
                "Farmer",
                "rice",
                "ধান",
                "DHK01",
                DecisionPath.PRIMARY.name(),
                DISEASE,
                "ব্লাস্ট",
                new BigDecimal("0.91"),
                1,
                false,
                AiMode.REPLAY.name(),
                ReviewState.PENDING.name(),
                null,
                false,
                (short) 0,
                T0,
                T0.plus(Duration.ofHours(4)),
                T0.plus(Duration.ofHours(1)),
                null);
        when(reads.findQueueRow(TASK)).thenReturn(Optional.of(row));
        when(tasks.findById(TASK)).thenReturn(Optional.of(
                ReviewTask.createPending(TASK, CASE, new BigDecimal("0.91"), T0.plus(Duration.ofHours(4)), T0)));
        when(cases.findById(CASE)).thenReturn(Optional.of(new CaseSummary(
                CASE,
                FARMER,
                CROP,
                "rice",
                "DHK01",
                "DHK",
                CaseStatus.IN_REVIEW,
                DecisionPath.PRIMARY,
                null,
                null,
                List.of(),
                null,
                "corr",
                T0,
                new BigDecimal("2"),
                FieldAreaUnit.DECIMAL,
                null,
                null,
                MetricsSource.FORM)));
        when(analysisApi.findByCaseId(CASE)).thenReturn(Optional.of(new AnalysisView(
                CASE,
                DecisionPath.PRIMARY,
                AiMode.REPLAY,
                new BigDecimal("0.91"),
                new BigDecimal("0.40"),
                new BigDecimal("0.51"),
                List.of(new CandidateView(DISEASE, "blast", "ব্লাস্ট", new BigDecimal("0.91"), 1, CandidateSource.MODEL)),
                List.of(),
                null,
                null,
                null,
                List.of(),
                "m",
                "v",
                1,
                null)));
        when(knowledge.listActiveRemedies(DISEASE)).thenReturn(List.of(new RemedyView(
                REMEDY,
                DISEASE,
                RemedyType.CHEMICAL,
                "title",
                List.of("step"),
                "dosage",
                14,
                "LOW",
                "HIGH",
                "src",
                new BigDecimal("50"),
                RemedyRateUnit.ML,
                RemedyRateBasis.PER_DECIMAL,
                null)));
        when(advisories.findPublishedByCaseId(CASE)).thenReturn(Optional.empty());

        ReviewTaskDetailView detail = handler().handle(new ReviewTaskDetailQuery(TASK, OFFICER_A));

        assertThat(detail.suggestedRemedies()).hasSize(1);
        RemedyRefView rem = detail.suggestedRemedies().getFirst();
        assertThat(rem.rateAmount()).isEqualByComparingTo("50");
        assertThat(rem.rateUnit()).isEqualTo(RemedyRateUnit.ML);
        assertThat(rem.rateBasis()).isEqualTo(RemedyRateBasis.PER_DECIMAL);
        assertThat(rem.computedDose()).isNotNull();
        assertThat(rem.computedDose().amount()).isEqualByComparingTo("100.000");
        assertThat(rem.computedDose().unit()).isEqualTo(RemedyRateUnit.ML);
        assertThat(rem.computedDose().fromArea()).isEqualByComparingTo("2");
        assertThat(rem.computedDose().fromAreaUnit()).isEqualTo(FieldAreaUnit.DECIMAL);
    }

    private ReviewTaskDetailQueryHandler handler() {
        return new ReviewTaskDetailQueryHandler(
                reads,
                tasks,
                advisories,
                analysisApi,
                cases,
                knowledge,
                officers,
                districtGuard,
                Clock.fixed(T0, ZoneOffset.UTC),
                Duration.ofMinutes(15));
    }
}

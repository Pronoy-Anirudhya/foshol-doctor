package com.rootcause.foshol.review.application.query.handler;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailQuery;
import com.rootcause.foshol.review.domain.ReviewException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class ReviewTaskDetailQueryHandlerTest {

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

    @Test
    void missingTask() {
        when(reads.findQueueRow(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        ReviewTaskDetailQueryHandler handler = new ReviewTaskDetailQueryHandler(
                reads,
                tasks,
                advisories,
                analysisApi,
                cases,
                knowledge,
                officers,
                Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(15));
        assertThatThrownBy(() -> handler.handle(new ReviewTaskDetailQuery(Uuid7.create())))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_REVIEW_TASK_NOT_FOUND);
    }
}

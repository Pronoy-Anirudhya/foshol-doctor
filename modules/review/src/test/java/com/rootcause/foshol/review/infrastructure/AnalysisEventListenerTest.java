package com.rootcause.foshol.review.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.ReviewFixtures;
import com.rootcause.foshol.review.application.port.OfficerQueueProjection;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisEventListenerTest {

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private OfficerQueueProjectionPort queue;

    @Mock
    private CaseIntakeApi cases;

    @Mock
    private FarmerLookupApi farmers;

    @Mock
    private KnowledgeQueryApi knowledge;

    private AnalysisEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AnalysisEventListener(
                tasks,
                queue,
                cases,
                farmers,
                knowledge,
                Clock.fixed(ReviewFixtures.T0, ZoneOffset.UTC),
                Duration.ofHours(4));
        lenient().when(tasks.findByCaseId(any())).thenReturn(Optional.empty());
        lenient().when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(farmers.findById(any())).thenReturn(Optional.of(ReviewFixtures.farmer()));
        lenient().when(cases.findById(any())).thenReturn(Optional.empty());
        lenient().when(knowledge.findCropById(any())).thenReturn(Optional.empty());
    }

    @Test
    void completedCreatesPendingTaskWithConfidence() {
        var event = ReviewFixtures.analysisCompleted(DecisionPath.PRIMARY, new BigDecimal("0.9100"));
        listener.onCompleted(event);
        ArgumentCaptor<ReviewTask> task = ArgumentCaptor.forClass(ReviewTask.class);
        verify(tasks).save(task.capture());
        assertThat(task.getValue().priorityConfidence()).isEqualByComparingTo("0.9100");
        assertThat(task.getValue().slaDueAt()).isEqualTo(ReviewFixtures.T0.plus(Duration.ofHours(4)));
        ArgumentCaptor<OfficerQueueProjection> row = ArgumentCaptor.forClass(OfficerQueueProjection.class);
        verify(queue).insert(row.capture(), any());
        assertThat(row.getValue().topConfidence()).isEqualByComparingTo("0.9100");
        assertThat(row.getValue().analysisMode()).isNotNull();
    }

    @Test
    void failedCreatesPendingTaskWithNullConfidence() {
        var event = ReviewFixtures.analysisFailed();
        listener.onFailed(event);
        ArgumentCaptor<ReviewTask> task = ArgumentCaptor.forClass(ReviewTask.class);
        verify(tasks).save(task.capture());
        assertThat(task.getValue().priorityConfidence()).isNull();
        ArgumentCaptor<OfficerQueueProjection> row = ArgumentCaptor.forClass(OfficerQueueProjection.class);
        verify(queue).insert(row.capture(), any());
        assertThat(row.getValue().topConfidence()).isNull();
        assertThat(row.getValue().analysisMode()).isNull();
    }

    @Test
    void duplicateCompletedIsIgnored() {
        var event = ReviewFixtures.analysisCompleted(DecisionPath.PRIMARY, new BigDecimal("0.50"));
        when(tasks.findByCaseId(event.caseId()))
                .thenReturn(Optional.of(ReviewTask.createPending(
                        Uuid7.create(), event.caseId(), null, ReviewFixtures.T0, ReviewFixtures.T0)));
        listener.onCompleted(event);
        verify(tasks, times(0)).save(any());
    }

    @Test
    void enrichmentFailureStillCreatesTask() {
        var event = ReviewFixtures.analysisCompleted(DecisionPath.UNDETERMINED, new BigDecimal("0.20"));
        when(knowledge.findCropById(any())).thenThrow(new RuntimeException("down"));
        listener.onCompleted(event);
        verify(tasks).save(any());
        ArgumentCaptor<OfficerQueueProjection> row = ArgumentCaptor.forClass(OfficerQueueProjection.class);
        verify(queue).insert(row.capture(), any());
        assertThat(row.getValue().cropNameBn()).isEmpty();
    }
}

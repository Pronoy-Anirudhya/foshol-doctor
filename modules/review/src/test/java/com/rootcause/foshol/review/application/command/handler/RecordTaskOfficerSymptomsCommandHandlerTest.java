package com.rootcause.foshol.review.application.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import com.rootcause.foshol.review.ReviewFixtures;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordTaskOfficerSymptomsCommandHandlerTest {

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private AnalysisApi analysisApi;

    @Mock
    private KnowledgeQueryApi knowledge;

    @Test
    void recordsKnownSymptoms() {
        UUID s1 = Uuid7.create();
        UUID s2 = Uuid7.create();
        ReviewTask task = claimed();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(knowledge.listSymptoms())
                .thenReturn(List.of(
                        new SymptomRefView(s1, "a", "a", "a", "LEAF"),
                        new SymptomRefView(s2, "b", "b", "b", "LEAF")));
        handler().handle(new RecordOfficerSymptomsCommand(task.id(), ReviewFixtures.OFFICER_A, List.of(s1, s2)));
        verify(analysisApi).recordOfficerSymptoms(task.caseId(), List.of(s1, s2));
    }

    @Test
    void unknownSymptomDoesNotCallAnalysis() {
        ReviewTask task = claimed();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(knowledge.listSymptoms()).thenReturn(List.of());
        assertThatThrownBy(() -> handler()
                        .handle(new RecordOfficerSymptomsCommand(
                                task.id(), ReviewFixtures.OFFICER_A, List.of(Uuid7.create()))))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_UNKNOWN_SYMPTOM);
        verify(analysisApi, never()).recordOfficerSymptoms(any(), any());
    }

    private RecordTaskOfficerSymptomsCommandHandler handler() {
        return new RecordTaskOfficerSymptomsCommandHandler(
                tasks,
                analysisApi,
                knowledge,
                Clock.fixed(ReviewFixtures.T0.plusSeconds(1), ZoneOffset.UTC),
                Duration.ofMinutes(15));
    }

    private static ReviewTask claimed() {
        ReviewTask task = ReviewTask.createPending(
                Uuid7.create(), Uuid7.create(), null, ReviewFixtures.T0.plus(Duration.ofHours(4)), ReviewFixtures.T0);
        task.claim(ReviewFixtures.OFFICER_A, ReviewFixtures.T0, Duration.ofMinutes(15));
        return task;
    }
}

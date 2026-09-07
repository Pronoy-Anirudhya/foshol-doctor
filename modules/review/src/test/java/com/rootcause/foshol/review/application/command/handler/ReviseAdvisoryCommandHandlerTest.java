package com.rootcause.foshol.review.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.ReviewFixtures;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReviseAdvisoryCommandHandlerTest {

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private AdvisoryRepository advisories;

    @Mock
    private OfficerQueueProjectionPort queue;

    @Mock
    private AnalysisApi analysisApi;

    @Mock
    private KnowledgeQueryApi knowledge;

    @Mock
    private OfficerLookupApi officers;

    @Mock
    private CaseIntakeApi cases;

    @Mock
    private ApplicationEventPublisher events;

    @Test
    void missingAdvisory() {
        when(advisories.findById(any())).thenReturn(Optional.empty());
        ReviseAdvisoryCommandHandler handler = handler();
        assertThatThrownBy(() -> handler.handle(new ReviseAdvisoryCommand(
                        Uuid7.create(), ReviewFixtures.OFFICER_A, ReviewFixtures.DISEASE_D, List.of(), null)))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_ADVISORY_NOT_FOUND);
    }

    @Test
    void revisesPublishedAdvisory() {
        ReviewTask task = ReviewTask.createPending(
                Uuid7.create(), Uuid7.create(), null, ReviewFixtures.T0.plus(Duration.ofHours(4)), ReviewFixtures.T0);
        task.claim(ReviewFixtures.OFFICER_A, ReviewFixtures.T0, Duration.ofMinutes(15));
        task.markDone(ReviewFixtures.T0.plusSeconds(1), ReviewFixtures.OFFICER_A.toString());
        Advisory v1 = Advisory.firstVersion(
                Uuid7.create(),
                task.caseId(),
                ReviewFixtures.DISEASE_D,
                ReviewFixtures.OFFICER_A,
                com.rootcause.foshol.common.AdvisoryAction.APPROVED,
                null,
                List.of(),
                ReviewFixtures.T0);
        when(advisories.findById(v1.id())).thenReturn(Optional.of(v1));
        when(advisories.findPublishedByCaseId(task.caseId())).thenReturn(Optional.of(v1));
        when(tasks.findByCaseId(task.caseId())).thenReturn(Optional.of(task));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(knowledge.findDiseaseById(ReviewFixtures.DISEASE_HEALTHY)).thenReturn(Optional.of(ReviewFixtures.healthy()));
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_HEALTHY)).thenReturn(List.of());
        when(analysisApi.findByCaseId(task.caseId())).thenReturn(Optional.of(ReviewFixtures.analysisView(task.caseId())));
        when(officers.findById(any())).thenReturn(Optional.of(ReviewFixtures.officer()));
        when(cases.findById(any())).thenReturn(Optional.empty());
        ApproveCaseResult result = handler()
                .handle(new ReviseAdvisoryCommand(
                        v1.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.DISEASE_HEALTHY, List.of(), null));
        assertThat(result.advisory().version()).isEqualTo(2);
        assertThat(result.advisory().supersedesId()).isEqualTo(v1.id());
    }

    private ReviseAdvisoryCommandHandler handler() {
        ApproveCaseCommandHandler approve = new ApproveCaseCommandHandler(
                tasks,
                advisories,
                queue,
                analysisApi,
                knowledge,
                officers,
                cases,
                events,
                Clock.fixed(ReviewFixtures.T0.plusSeconds(5), ZoneOffset.UTC),
                Duration.ofMinutes(15));
        return new ReviseAdvisoryCommandHandler(
                tasks,
                advisories,
                queue,
                knowledge,
                officers,
                cases,
                events,
                Clock.fixed(ReviewFixtures.T0.plusSeconds(5), ZoneOffset.UTC),
                approve);
    }
}

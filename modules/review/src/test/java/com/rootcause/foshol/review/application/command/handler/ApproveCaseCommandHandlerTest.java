package com.rootcause.foshol.review.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.common.AdvisoryAction;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.application.command.ApproveCaseCommand;
import com.rootcause.foshol.review.application.command.ApproveCaseResult;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.review.ReviewFixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ApproveCaseCommandHandlerTest {

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

    private ApproveCaseCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApproveCaseCommandHandler(
                tasks,
                advisories,
                queue,
                analysisApi,
                knowledge,
                officers,
                cases,
                events,
                Clock.fixed(ReviewFixtures.T0.plusSeconds(1), ZoneOffset.UTC),
                Duration.ofMinutes(15));
    }

    @Test
    void matchingRemediesYieldApproved() {
        ReviewTask task = claimed();
        stubLookups(task);
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_D))
                .thenReturn(List.of(ReviewFixtures.r1(), ReviewFixtures.r2()));
        ApproveCaseResult result = handler.handle(new ApproveCaseCommand(
                task.id(),
                ReviewFixtures.OFFICER_A,
                ReviewFixtures.DISEASE_D,
                List.of(ReviewFixtures.REMEDY_R1, ReviewFixtures.REMEDY_R2),
                null));
        ArgumentCaptor<Advisory> captor = ArgumentCaptor.forClass(Advisory.class);
        verify(advisories).insert(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AdvisoryAction.APPROVED);
        assertThat(result.advisory().action()).isEqualTo(AdvisoryAction.APPROVED);
    }

    @Test
    void subsetRemediesYieldEdited() {
        ReviewTask task = claimed();
        stubLookups(task);
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_D))
                .thenReturn(List.of(ReviewFixtures.r1(), ReviewFixtures.r2()));
        handler.handle(new ApproveCaseCommand(
                task.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.DISEASE_D, List.of(ReviewFixtures.REMEDY_R1), null));
        ArgumentCaptor<Advisory> captor = ArgumentCaptor.forClass(Advisory.class);
        verify(advisories).insert(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AdvisoryAction.EDITED);
    }

    @Test
    void otherDiseaseYieldsReplaced() {
        ReviewTask task = claimed();
        stubLookups(task);
        when(knowledge.findDiseaseById(ReviewFixtures.DISEASE_E)).thenReturn(Optional.of(ReviewFixtures.diseaseE()));
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_E)).thenReturn(List.of(ReviewFixtures.r3()));
        handler.handle(new ApproveCaseCommand(
                task.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.DISEASE_E, List.of(ReviewFixtures.REMEDY_R3), null));
        ArgumentCaptor<Advisory> captor = ArgumentCaptor.forClass(Advisory.class);
        verify(advisories).insert(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AdvisoryAction.REPLACED);
    }

    @Test
    void emptyRemediesOnSickDiseaseRejected() {
        ReviewTask task = claimed();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(knowledge.findDiseaseById(ReviewFixtures.DISEASE_D)).thenReturn(Optional.of(ReviewFixtures.diseaseD()));
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_D)).thenReturn(List.of(ReviewFixtures.r1()));
        assertThatThrownBy(() -> handler.handle(new ApproveCaseCommand(
                        task.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.DISEASE_D, List.of(), null)))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_ADVISORY_REQUIRES_REMEDY);
        verify(advisories, never()).insert(any());
    }

    @Test
    void healthyDiseaseAllowsEmptyRemedies() {
        ReviewTask task = claimed();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(knowledge.findDiseaseById(ReviewFixtures.DISEASE_HEALTHY)).thenReturn(Optional.of(ReviewFixtures.healthy()));
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_HEALTHY)).thenReturn(List.of());
        when(analysisApi.findByCaseId(task.caseId())).thenReturn(Optional.of(ReviewFixtures.analysisView(task.caseId())));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(officers.findById(any())).thenReturn(Optional.of(ReviewFixtures.officer()));
        handler.handle(new ApproveCaseCommand(
                task.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.DISEASE_HEALTHY, List.of(), null));
        ArgumentCaptor<Advisory> captor = ArgumentCaptor.forClass(Advisory.class);
        verify(advisories).insert(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AdvisoryAction.REPLACED);
    }

    @Test
    void chemicalWithoutPhiRejected() {
        ReviewTask task = claimed();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(knowledge.findDiseaseById(ReviewFixtures.DISEASE_D)).thenReturn(Optional.of(ReviewFixtures.diseaseD()));
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_D))
                .thenReturn(List.of(ReviewFixtures.chemicalWithoutPhi()));
        assertThatThrownBy(() -> handler.handle(new ApproveCaseCommand(
                        task.id(),
                        ReviewFixtures.OFFICER_A,
                        ReviewFixtures.DISEASE_D,
                        List.of(ReviewFixtures.REMEDY_NO_PHI),
                        null)))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_REMEDY_PHI_MISSING);
    }

    @Test
    void foreignRemedyRejected() {
        ReviewTask task = claimed();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(knowledge.findDiseaseById(ReviewFixtures.DISEASE_D)).thenReturn(Optional.of(ReviewFixtures.diseaseD()));
        when(knowledge.listActiveRemedies(ReviewFixtures.DISEASE_D)).thenReturn(List.of(ReviewFixtures.r1()));
        assertThatThrownBy(() -> handler.handle(new ApproveCaseCommand(
                        task.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.DISEASE_D, List.of(ReviewFixtures.REMEDY_R3), null)))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_REMEDY_DISEASE_MISMATCH);
    }

    @Test
    void expiredClaimCannotApprove() {
        ReviewTask task = claimed();
        handler = new ApproveCaseCommandHandler(
                tasks,
                advisories,
                queue,
                analysisApi,
                knowledge,
                officers,
                cases,
                events,
                Clock.fixed(ReviewFixtures.T0.plus(Duration.ofMinutes(16)), ZoneOffset.UTC),
                Duration.ofMinutes(15));
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> handler.handle(new ApproveCaseCommand(
                        task.id(), ReviewFixtures.OFFICER_A, ReviewFixtures.DISEASE_D, List.of(ReviewFixtures.REMEDY_R1), null)))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CLAIM_NOT_HELD);
        verify(advisories, never()).insert(any());
    }

    private void stubLookups(ReviewTask task) {
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        lenient().when(knowledge.findDiseaseById(ReviewFixtures.DISEASE_D)).thenReturn(Optional.of(ReviewFixtures.diseaseD()));
        when(analysisApi.findByCaseId(task.caseId())).thenReturn(Optional.of(ReviewFixtures.analysisView(task.caseId())));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(officers.findById(any())).thenReturn(Optional.of(ReviewFixtures.officer()));
        when(cases.findById(any())).thenReturn(Optional.empty());
    }

    private static ReviewTask claimed() {
        ReviewTask task = ReviewTask.createPending(
                UUID.randomUUID(), UUID.randomUUID(), null, ReviewFixtures.T0.plus(Duration.ofHours(4)), ReviewFixtures.T0);
        task.claim(ReviewFixtures.OFFICER_A, ReviewFixtures.T0, Duration.ofMinutes(15));
        return task;
    }
}

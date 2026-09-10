package com.rootcause.foshol.review.application.command.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.RejectionReason;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.review.application.command.RejectCaseCommand;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.CaseRejectionRepository;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.review.ReviewFixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RejectCaseCommandHandlerTest {

    @Mock
    private ReviewTaskRepository tasks;

    @Mock
    private CaseRejectionRepository rejections;

    @Mock
    private AdvisoryRepository advisories;

    @Mock
    private OfficerQueueProjectionPort queue;

    @Mock
    private OfficerLookupApi officers;

    @Mock
    private CaseIntakeApi cases;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private com.rootcause.foshol.review.application.command.ReviewDistrictGuard districtGuard;

    @Test
    void writesRejectionAndNoAdvisory() {
        ReviewTask task = ReviewTask.createPending(
                Uuid7.create(), Uuid7.create(), null, ReviewFixtures.T0.plus(Duration.ofHours(4)), ReviewFixtures.T0);
        task.claim(ReviewFixtures.OFFICER_A, ReviewFixtures.T0, Duration.ofMinutes(15));
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(advisories.findPublishedByCaseId(task.caseId())).thenReturn(Optional.empty());
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(officers.findById(any())).thenReturn(Optional.of(ReviewFixtures.officer()));
        when(cases.findById(any())).thenReturn(Optional.empty());
        RejectCaseCommandHandler handler = new RejectCaseCommandHandler(
                tasks,
                rejections,
                advisories,
                queue,
                officers,
                cases,
                districtGuard,
                events,
                Clock.fixed(ReviewFixtures.T0.plusSeconds(1), ZoneOffset.UTC),
                Duration.ofMinutes(15));
        handler.handle(new RejectCaseCommand(
                task.id(), ReviewFixtures.OFFICER_A, RejectionReason.BLURRY_IMAGE, "ছবি"));
        verify(rejections).insert(any());
        verify(advisories, never()).insert(any());
        verify(events).publishEvent(any(Object.class));
    }
}

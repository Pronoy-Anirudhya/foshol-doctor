package com.rootcause.foshol.notification.application.command.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.AdvisoryAction;
import com.rootcause.foshol.common.enums.NotificationType;
import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.notification.NotifyFixtures;
import com.rootcause.foshol.notification.application.command.DeliveryService;
import com.rootcause.foshol.notification.application.command.NotificationContentAssembler;
import com.rootcause.foshol.notification.application.port.NotificationRepository;
import com.rootcause.foshol.notification.application.command.NotificationTemplates;
import com.rootcause.foshol.notification.domain.Notification;
import com.rootcause.foshol.review.api.ReviewSubmissionApi;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdvisoryApprovedEventHandlerTest {

    @Mock
    private FarmerLookupApi farmers;

    @Mock
    private ReviewSubmissionApi review;

    @Mock
    private NotificationRepository notifications;

    @Mock
    private DeliveryService delivery;

    private AdvisoryApprovedEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AdvisoryApprovedEventHandler(
                farmers,
                review,
                notifications,
                new NotificationContentAssembler(new NotificationTemplates()),
                delivery,
                Clock.fixed(NotifyFixtures.T0, ZoneOffset.UTC));
    }

    @Test
    void deliversPublishedAdvisory() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.of(NotifyFixtures.farmer()));
        when(notifications.findDuplicate(any(), any(), eq(NotificationType.ADVISORY_PUBLISHED), any()))
                .thenReturn(Optional.empty());
        when(review.findPublishedAdvisory(NotifyFixtures.CASE)).thenReturn(Optional.of(NotifyFixtures.advisoryView()));
        handler.handle(event());
        verify(delivery).deliver(any(Notification.class));
    }

    @Test
    void unknownFarmerWritesNothing() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.empty());
        handler.handle(event());
        verify(delivery, never()).deliver(any());
        verify(notifications, never()).insert(any());
    }

    @Test
    void duplicateIsNoOp() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.of(NotifyFixtures.farmer()));
        when(notifications.findDuplicate(any(), any(), eq(NotificationType.ADVISORY_PUBLISHED), any()))
                .thenReturn(Optional.of(Notification.pending(
                        NotifyFixtures.ADVISORY,
                        NotifyFixtures.FARMER,
                        NotifyFixtures.CASE,
                        NotifyFixtures.ADVISORY,
                        NotificationType.ADVISORY_PUBLISHED,
                        "t",
                        "b",
                        java.util.Map.of(),
                        NotifyFixtures.T0)));
        handler.handle(event());
        verify(delivery, never()).deliver(any());
    }

    private static AdvisoryApproved event() {
        return new AdvisoryApproved(
                NotifyFixtures.ADVISORY,
                NotifyFixtures.CASE,
                NotifyFixtures.FARMER,
                NotifyFixtures.OFFICER,
                "Officer A",
                NotifyFixtures.advisoryView().diseaseId(),
                "d-name",
                AdvisoryAction.APPROVED,
                1,
                NotifyFixtures.CORRELATION,
                NotifyFixtures.T0);
    }
}

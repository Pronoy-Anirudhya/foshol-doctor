package com.rootcause.foshol.notification.application.command.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.NotificationType;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.common.events.AdvisoryRevised;
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
class AdvisoryRevisedEventHandlerTest {

    @Mock
    private FarmerLookupApi farmers;

    @Mock
    private ReviewSubmissionApi review;

    @Mock
    private NotificationRepository notifications;

    @Mock
    private DeliveryService delivery;

    private AdvisoryRevisedEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AdvisoryRevisedEventHandler(
                farmers,
                review,
                notifications,
                new NotificationContentAssembler(new NotificationTemplates()),
                delivery,
                Clock.fixed(NotifyFixtures.T0, ZoneOffset.UTC));
    }

    @Test
    void deliversRevision() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.of(NotifyFixtures.farmer()));
        when(notifications.findDuplicate(any(), any(), eq(NotificationType.ADVISORY_REVISED), any()))
                .thenReturn(Optional.empty());
        when(review.findPublishedAdvisory(NotifyFixtures.CASE)).thenReturn(Optional.of(NotifyFixtures.advisoryView()));
        handler.handle(event());
        verify(delivery).deliver(any(Notification.class));
    }

    @Test
    void duplicateIsNoOp() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.of(NotifyFixtures.farmer()));
        when(notifications.findDuplicate(any(), any(), eq(NotificationType.ADVISORY_REVISED), any()))
                .thenReturn(Optional.of(Notification.pending(
                        Uuid7.create(),
                        NotifyFixtures.FARMER,
                        NotifyFixtures.CASE,
                        NotifyFixtures.ADVISORY,
                        NotificationType.ADVISORY_REVISED,
                        "t",
                        "b",
                        java.util.Map.of(),
                        NotifyFixtures.T0)));
        handler.handle(event());
        verify(delivery, never()).deliver(any());
    }

    private static AdvisoryRevised event() {
        return new AdvisoryRevised(
                NotifyFixtures.ADVISORY,
                Uuid7.create(),
                NotifyFixtures.CASE,
                NotifyFixtures.FARMER,
                NotifyFixtures.OFFICER,
                "Officer A",
                2,
                NotifyFixtures.CORRELATION,
                NotifyFixtures.T0);
    }
}

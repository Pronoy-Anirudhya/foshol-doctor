package com.rootcause.foshol.notification.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.RejectionReason;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.notification.NotifyFixtures;
import com.rootcause.foshol.notification.application.DeliveryService;
import com.rootcause.foshol.notification.application.NotificationContentAssembler;
import com.rootcause.foshol.notification.application.NotificationRepository;
import com.rootcause.foshol.notification.application.NotificationTemplates;
import com.rootcause.foshol.notification.domain.Notification;
import com.rootcause.foshol.review.api.ReviewSubmissionApi;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleCaseRejectedTest {

    @Mock
    private FarmerLookupApi farmers;

    @Mock
    private ReviewSubmissionApi review;

    @Mock
    private NotificationRepository notifications;

    @Mock
    private DeliveryService delivery;

    private HandleCaseRejected handler;

    @BeforeEach
    void setUp() {
        handler = new HandleCaseRejected(
                farmers,
                review,
                notifications,
                new NotificationContentAssembler(new NotificationTemplates()),
                delivery,
                Clock.fixed(NotifyFixtures.T0, ZoneOffset.UTC));
    }

    @Test
    void bodyContainsOfficerMessage() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.of(NotifyFixtures.farmer()));
        when(notifications.findDuplicate(any(), any(), eq(NotificationType.CASE_REJECTED), any()))
                .thenReturn(Optional.empty());
        when(review.findRejection(NotifyFixtures.CASE)).thenReturn(Optional.of(NotifyFixtures.rejectionView()));
        handler.handle(event());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(delivery).deliver(captor.capture());
        assertThat(captor.getValue().bodyBn()).isEqualTo(com.rootcause.foshol.common.BanglaNormalizer.forStorage(NotifyFixtures.FIXTURE_MESSAGE));
        assertThat(captor.getValue().advisoryId()).isNull();
        assertThat(captor.getValue().payload().get("reasonCode")).isEqualTo(RejectionReason.BLURRY_IMAGE.name());
    }

    @Test
    void unknownFarmerWritesNothing() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.empty());
        handler.handle(event());
        verify(delivery, never()).deliver(any());
    }

    private static CaseRejected event() {
        return new CaseRejected(
                NotifyFixtures.CASE,
                NotifyFixtures.FARMER,
                NotifyFixtures.OFFICER,
                "Officer A",
                RejectionReason.BLURRY_IMAGE,
                NotifyFixtures.FIXTURE_MESSAGE,
                NotifyFixtures.CORRELATION,
                NotifyFixtures.T0);
    }
}

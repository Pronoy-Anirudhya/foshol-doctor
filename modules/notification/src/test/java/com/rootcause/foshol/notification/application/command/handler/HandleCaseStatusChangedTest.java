package com.rootcause.foshol.notification.application.command.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.notification.NotifyFixtures;
import com.rootcause.foshol.notification.application.DeliveryService;
import com.rootcause.foshol.notification.application.NotificationContentAssembler;
import com.rootcause.foshol.notification.application.NotificationRepository;
import com.rootcause.foshol.notification.application.NotificationTemplates;
import com.rootcause.foshol.notification.application.OfficerQueueNudgePort;
import com.rootcause.foshol.notification.domain.Notification;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleCaseStatusChangedTest {

    @Mock
    private FarmerLookupApi farmers;

    @Mock
    private NotificationRepository notifications;

    @Mock
    private DeliveryService delivery;

    @Mock
    private OfficerQueueNudgePort nudge;

    private HandleCaseStatusChanged handler;

    @BeforeEach
    void setUp() {
        handler = new HandleCaseStatusChanged(
                farmers,
                notifications,
                new NotificationContentAssembler(new NotificationTemplates()),
                delivery,
                nudge,
                Clock.fixed(NotifyFixtures.T0, ZoneOffset.UTC));
    }

    @Test
    void deliversAndNudgesOfficers() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.of(NotifyFixtures.farmer()));
        when(notifications.findDuplicate(any(), any(), eq(NotificationType.CASE_STATUS_CHANGED), any()))
                .thenReturn(Optional.empty());
        handler.handle(event(CaseStatus.ANALYSING, CaseStatus.ANALYSED));
        verify(delivery).deliver(any(Notification.class));
        verify(nudge).emitQueue(NotifyFixtures.CASE, CaseStatus.ANALYSED.name(), NotifyFixtures.CORRELATION);
    }

    @Test
    void duplicateStillNudgesButDoesNotDeliver() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.of(NotifyFixtures.farmer()));
        when(notifications.findDuplicate(any(), any(), eq(NotificationType.CASE_STATUS_CHANGED), eq("ANALYSED")))
                .thenReturn(Optional.of(Notification.pending(
                        NotifyFixtures.CASE,
                        NotifyFixtures.FARMER,
                        NotifyFixtures.CASE,
                        null,
                        NotificationType.CASE_STATUS_CHANGED,
                        "t",
                        "b",
                        java.util.Map.of("toStatus", "ANALYSED"),
                        NotifyFixtures.T0)));
        handler.handle(event(CaseStatus.ANALYSING, CaseStatus.ANALYSED));
        verify(delivery, never()).deliver(any());
        verify(nudge).emitQueue(NotifyFixtures.CASE, "ANALYSED", NotifyFixtures.CORRELATION);
    }

    @Test
    void stillNudgesOfficersWhenFarmerDeliveryThrows() {
        when(farmers.findById(NotifyFixtures.FARMER)).thenReturn(Optional.of(NotifyFixtures.farmer()));
        when(notifications.findDuplicate(any(), any(), eq(NotificationType.CASE_STATUS_CHANGED), any()))
                .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("delivery failed")).when(delivery).deliver(any(Notification.class));
        assertThatThrownBy(() -> handler.handle(event(CaseStatus.ANALYSING, CaseStatus.ANALYSED)))
                .isInstanceOf(IllegalStateException.class);
        verify(nudge).emitQueue(NotifyFixtures.CASE, CaseStatus.ANALYSED.name(), NotifyFixtures.CORRELATION);
    }

    private static CaseStatusChanged event(CaseStatus from, CaseStatus to) {
        return new CaseStatusChanged(
                NotifyFixtures.CASE, NotifyFixtures.FARMER, from, to, NotifyFixtures.CORRELATION, NotifyFixtures.T0);
    }
}

package com.rootcause.foshol.notification.application.query.handler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.notification.application.NotificationRepository;
import com.rootcause.foshol.notification.application.query.FarmerNotificationsPage;
import com.rootcause.foshol.notification.application.query.FarmerNotificationsQuery;
import com.rootcause.foshol.notification.domain.DeliveryState;
import com.rootcause.foshol.notification.domain.Notification;
import com.rootcause.foshol.notification.NotifyFixtures;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class FarmerNotificationsQueryHandlerTest {

    @Mock
    private NotificationRepository notifications;

    @Test
    void returnsPageForFarmer() {
        Notification row = Notification.pending(
                Uuid7.create(),
                NotifyFixtures.FARMER,
                NotifyFixtures.CASE,
                NotifyFixtures.ADVISORY,
                NotificationType.ADVISORY_PUBLISHED,
                "t",
                "b",
                Map.of(),
                NotifyFixtures.T0);
        when(notifications.countByFarmer(NotifyFixtures.FARMER)).thenReturn(1L);
        when(notifications.findByFarmer(NotifyFixtures.FARMER, 0, 20)).thenReturn(List.of(row));
        FarmerNotificationsPage page =
                new FarmerNotificationsQueryHandler(notifications)
                        .handle(new FarmerNotificationsQuery(NotifyFixtures.FARMER, 0, 20));
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().state()).isEqualTo(DeliveryState.PENDING);
    }
}

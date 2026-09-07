package com.rootcause.foshol.notification.application.query;

import com.rootcause.foshol.notification.application.NotificationRepository;
import com.rootcause.foshol.notification.domain.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerNotificationsQueryHandler {

    private final NotificationRepository notifications;

    public FarmerNotificationsQueryHandler(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public FarmerNotificationsPage handle(FarmerNotificationsQuery query) {
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        int page = Math.max(query.page(), 0);
        long total = notifications.countByFarmer(query.farmerId());
        var rows = notifications.findByFarmer(query.farmerId(), page, size).stream()
                .map(this::toRow)
                .toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new FarmerNotificationsPage(rows, page, size, total, totalPages);
    }

    private FarmerNotificationRow toRow(Notification n) {
        return new FarmerNotificationRow(
                n.id(),
                n.caseId(),
                n.advisoryId(),
                n.type(),
                n.titleBn(),
                n.bodyBn(),
                n.channel(),
                n.state(),
                n.deliveredAt(),
                n.createdAt());
    }
}

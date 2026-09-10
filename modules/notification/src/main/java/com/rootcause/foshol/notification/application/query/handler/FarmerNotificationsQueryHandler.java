package com.rootcause.foshol.notification.application.query.handler;

import com.rootcause.foshol.notification.application.port.NotificationRepository;
import com.rootcause.foshol.notification.application.query.FarmerNotificationRow;
import com.rootcause.foshol.notification.application.query.FarmerNotificationsPage;
import com.rootcause.foshol.notification.application.query.FarmerNotificationsQuery;
import com.rootcause.foshol.notification.domain.Notification;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerNotificationsQueryHandler implements QueryHandler<FarmerNotificationsQuery, FarmerNotificationsPage> {

    @Override
    public Class<FarmerNotificationsQuery> queryType() {
        return FarmerNotificationsQuery.class;
    }

    private final NotificationRepository notifications;

    public FarmerNotificationsQueryHandler(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    @Override
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

package com.rootcause.foshol.notification.application.port;

import com.rootcause.foshol.common.enums.NotificationType;
import com.rootcause.foshol.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    void insert(Notification notification);

    void update(Notification notification);

    Optional<Notification> findDuplicate(UUID farmerId, UUID caseId, NotificationType type, String dedupeKey);

    List<Notification> findByFarmer(UUID farmerId, int page, int size);

    long countByFarmer(UUID farmerId);
}

package com.rootcause.foshol.notification.application.query;

import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.notification.domain.DeliveryState;
import java.time.Instant;
import java.util.UUID;

public record FarmerNotificationRow(
        UUID id,
        UUID caseId,
        UUID advisoryId,
        NotificationType type,
        String titleBn,
        String bodyBn,
        String channel,
        DeliveryState state,
        Instant deliveredAt,
        Instant createdAt) {}

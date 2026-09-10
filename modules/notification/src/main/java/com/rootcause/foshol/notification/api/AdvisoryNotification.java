package com.rootcause.foshol.notification.api;

import com.rootcause.foshol.common.enums.NotificationType;
import java.util.Map;
import java.util.UUID;

public record AdvisoryNotification(
        UUID notificationId,
        UUID farmerId,
        UUID caseId,
        UUID advisoryId,
        NotificationType type,
        String titleBn,
        String bodyBn,
        Map<String, String> data) {}

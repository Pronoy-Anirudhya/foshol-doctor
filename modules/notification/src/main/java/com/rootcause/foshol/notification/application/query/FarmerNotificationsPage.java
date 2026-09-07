package com.rootcause.foshol.notification.application.query;

import java.util.List;

public record FarmerNotificationsPage(
        List<FarmerNotificationRow> content, int page, int size, long totalElements, int totalPages) {}

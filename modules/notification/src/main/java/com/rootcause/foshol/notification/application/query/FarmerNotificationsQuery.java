package com.rootcause.foshol.notification.application.query;

import java.util.UUID;

public record FarmerNotificationsQuery(UUID farmerId, int page, int size) {}

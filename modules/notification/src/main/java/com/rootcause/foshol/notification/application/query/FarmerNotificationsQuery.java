package com.rootcause.foshol.notification.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record FarmerNotificationsQuery(UUID farmerId, int page, int size) implements Query {}

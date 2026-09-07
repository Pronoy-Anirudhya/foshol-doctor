package com.rootcause.foshol.notification.api;

import java.util.UUID;

public interface NotificationChannel {

    String name();

    boolean enabled();

    boolean supports(UUID farmerId);

    boolean send(AdvisoryNotification notification);
}

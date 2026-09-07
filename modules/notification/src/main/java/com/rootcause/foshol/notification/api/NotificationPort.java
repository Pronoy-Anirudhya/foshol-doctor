package com.rootcause.foshol.notification.api;

public interface NotificationPort {

    void publish(AdvisoryNotification notification);
}

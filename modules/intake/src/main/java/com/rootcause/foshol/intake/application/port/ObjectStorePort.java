package com.rootcause.foshol.intake.application.port;

import java.time.Duration;

public interface ObjectStorePort {

    void put(String objectKey, byte[] bytes, String contentType);

    void deleteQuietly(String objectKey);

    String presignGet(String objectKey, Duration ttl);
}

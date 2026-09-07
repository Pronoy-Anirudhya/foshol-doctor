package com.rootcause.foshol.intake.application.port;

import java.time.Duration;

public interface ImageStorePort {

    StoredObject put(String objectKey, String contentType, byte[] bytes);

    String presign(String objectKey, Duration ttl);

    void delete(String objectKey);

    record StoredObject(String objectKey, int byteSize, String sha256) {}
}

package com.rootcause.foshol.analysis.application.port;

import java.time.Duration;

public interface ObjectStorePort {

    byte[] read(String objectKey);

    void write(String objectKey, byte[] bytes, String contentType);

    PresignedUrl presign(String objectKey, Duration ttl);
}

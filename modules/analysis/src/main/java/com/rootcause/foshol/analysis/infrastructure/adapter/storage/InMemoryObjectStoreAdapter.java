package com.rootcause.foshol.analysis.infrastructure.adapter.storage;

import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.PresignedUrl;
import com.rootcause.foshol.common.contract.ConfigKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = ConfigKeys.STORAGE_ENDPOINT, havingValue = "memory")
public class InMemoryObjectStoreAdapter implements ObjectStorePort {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public byte[] read(String objectKey) {
        byte[] bytes = objects.get(objectKey);
        return bytes == null ? new byte[0] : bytes;
    }

    @Override
    public void write(String objectKey, byte[] bytes, String contentType) {
        objects.put(objectKey, bytes == null ? new byte[0] : bytes);
    }

    @Override
    public PresignedUrl presign(String objectKey, Duration ttl) {
        Duration expiry = ttl == null ? Duration.ofMinutes(10) : ttl;
        return new PresignedUrl("/memory-objects/" + objectKey, Instant.now().plus(expiry));
    }
}

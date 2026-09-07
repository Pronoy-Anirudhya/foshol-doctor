package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.intake.application.port.ImageStorePort;
import com.rootcause.foshol.intake.application.port.ObjectStorePort;
import com.rootcause.foshol.intake.domain.vo.Sha256;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = ConfigKeys.STORAGE_ENDPOINT, havingValue = "memory")
public class InMemoryObjectStore implements ObjectStorePort, ImageStorePort {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) {
        objects.put(objectKey, bytes);
    }

    @Override
    public void deleteQuietly(String objectKey) {
        objects.remove(objectKey);
    }

    @Override
    public StoredObject put(String objectKey, String contentType, byte[] bytes) {
        put(objectKey, bytes, contentType);
        return new StoredObject(objectKey, bytes.length, Sha256.ofBytes(bytes).hex());
    }

    @Override
    public String presign(String objectKey, Duration ttl) {
        return presignGet(objectKey, ttl);
    }

    @Override
    public void delete(String objectKey) {
        deleteQuietly(objectKey);
    }

    @Override
    public String presignGet(String objectKey, Duration ttl) {
        return "/memory-objects/" + objectKey;
    }

    public byte[] get(String objectKey) {
        return objects.get(objectKey);
    }
}

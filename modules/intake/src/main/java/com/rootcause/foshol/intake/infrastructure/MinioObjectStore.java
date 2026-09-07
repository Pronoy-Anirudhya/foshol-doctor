package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.port.ImageStorePort;
import com.rootcause.foshol.intake.application.port.ObjectStorePort;
import com.rootcause.foshol.intake.domain.vo.Sha256;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!'memory'.equals('${foshol.storage.endpoint:}')")
public class MinioObjectStore implements ObjectStorePort, ImageStorePort {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStore.class);

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStore(
            @Value("${" + ConfigKeys.STORAGE_ENDPOINT + "}") String endpoint,
            @Value("${" + ConfigKeys.STORAGE_ACCESS_KEY + "}") String accessKey,
            @Value("${" + ConfigKeys.STORAGE_SECRET_KEY + "}") String secretKey,
            @Value("${" + ConfigKeys.STORAGE_BUCKET + "}") String bucket) {
        this.bucket = bucket;
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region("us-east-1")
                .build();
        if (!"memory".equals(endpoint)) {
            ensureBucket();
        }
    }

    private void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            log.warn("MinIO bucket check failed bucket={}", bucket, ex);
            throw new IntakeException(ErrorCodes.ERR_STORAGE_UNAVAILABLE, 503, "Object storage is unavailable.");
        }
    }

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            log.warn("MinIO put failed bucket={} key={}", bucket, objectKey, ex);
            throw new IntakeException(ErrorCodes.ERR_STORAGE_UNAVAILABLE, 503, "Object storage is unavailable.");
        }
    }

    @Override
    public void deleteQuietly(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ignored) {
            // best-effort cleanup
        }
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
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) Math.max(1, ttl.toSeconds()), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            log.warn("MinIO presign failed bucket={} key={}", bucket, objectKey, ex);
            throw new IntakeException(ErrorCodes.ERR_STORAGE_UNAVAILABLE, 503, "Object storage is unavailable.");
        }
    }
}

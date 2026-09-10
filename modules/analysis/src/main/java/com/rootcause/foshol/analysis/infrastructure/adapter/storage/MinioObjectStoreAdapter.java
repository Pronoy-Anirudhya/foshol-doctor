package com.rootcause.foshol.analysis.infrastructure.adapter.storage;

import com.rootcause.foshol.analysis.application.config.AnalysisSettings;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.PresignedUrl;
import com.rootcause.foshol.analysis.domain.AnalysisException;
import com.rootcause.foshol.common.contract.ErrorCodes;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
@ConditionalOnExpression("!'memory'.equals('${foshol.storage.endpoint:}')")
class MinioConfiguration {

    @Bean
    MinioClient minioClient(AnalysisSettings settings) {
        return MinioClient.builder()
                .endpoint(settings.storageEndpoint())
                .credentials(settings.storageAccessKey(), settings.storageSecretKey())
                .region("us-east-1")
                .build();
    }
}

@Component
@ConditionalOnExpression("!'memory'.equals('${foshol.storage.endpoint:}')")
public class MinioObjectStoreAdapter implements ObjectStorePort {

    private final MinioClient minio;
    private final AnalysisSettings settings;

    public MinioObjectStoreAdapter(MinioClient minio, AnalysisSettings settings) {
        this.minio = minio;
        this.settings = settings;
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            return minio.getObject(GetObjectArgs.builder()
                            .bucket(settings.storageBucket())
                            .object(objectKey)
                            .build())
                    .readAllBytes();
        } catch (Exception ex) {
            throw new AnalysisException(ErrorCodes.ERR_STORAGE_UNAVAILABLE, 503, "Unable to read object.");
        }
    }

    @Override
    public void write(String objectKey, byte[] bytes, String contentType) {
        try {
            ensureBucket();
            minio.putObject(PutObjectArgs.builder()
                    .bucket(settings.storageBucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new AnalysisException(ErrorCodes.ERR_STORAGE_UNAVAILABLE, 503, "Unable to write object.");
        }
    }

    @Override
    public PresignedUrl presign(String objectKey, Duration ttl) {
        try {
            int seconds = (int) Math.max(1, ttl.toSeconds());
            String url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(settings.storageBucket())
                    .object(objectKey)
                    .expiry(seconds, TimeUnit.SECONDS)
                    .build());
            return new PresignedUrl(url, Instant.now().plusSeconds(seconds));
        } catch (Exception ex) {
            throw new AnalysisException(ErrorCodes.ERR_STORAGE_UNAVAILABLE, 503, "Unable to presign object.");
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(settings.storageBucket()).build());
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(settings.storageBucket()).build());
        }
    }
}

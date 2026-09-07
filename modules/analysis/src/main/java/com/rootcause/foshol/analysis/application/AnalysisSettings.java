package com.rootcause.foshol.analysis.application;

import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.ConfigKeys;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnalysisSettings {

    private final AiMode aiMode;
    private final BigDecimal confidenceHigh;
    private final BigDecimal confidenceLow;
    private final BigDecimal temperature;
    private final String aggregation;
    private final Duration deadline;
    private final int candidateLimit;
    private final boolean gradcamEnabled;
    private final String aiBaseUrl;
    private final Duration aiTimeout;
    private final Duration presignTtl;
    private final String storageEndpoint;
    private final String storageAccessKey;
    private final String storageSecretKey;
    private final String storageBucket;

    public AnalysisSettings(
            @Value("${" + ConfigKeys.AI_MODE + "}") String aiMode,
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_HIGH + "}") BigDecimal confidenceHigh,
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_LOW + "}") BigDecimal confidenceLow,
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_TEMPERATURE + "}") BigDecimal temperature,
            @Value("${" + ConfigKeys.ANALYSIS_MULTI_IMAGE_AGGREGATION + "}") String aggregation,
            @Value("${" + ConfigKeys.ANALYSIS_DEADLINE + "}") Duration deadline,
            @Value("${" + ConfigKeys.ANALYSIS_CANDIDATE_LIMIT + "}") int candidateLimit,
            @Value("${" + ConfigKeys.ANALYSIS_GRADCAM_ENABLED + "}") boolean gradcamEnabled,
            @Value("${" + ConfigKeys.AI_BASE_URL + "}") String aiBaseUrl,
            @Value("${" + ConfigKeys.AI_TIMEOUT + "}") Duration aiTimeout,
            @Value("${" + ConfigKeys.STORAGE_PRESIGN_TTL + "}") Duration presignTtl,
            @Value("${" + ConfigKeys.STORAGE_ENDPOINT + "}") String storageEndpoint,
            @Value("${" + ConfigKeys.STORAGE_ACCESS_KEY + "}") String storageAccessKey,
            @Value("${" + ConfigKeys.STORAGE_SECRET_KEY + "}") String storageSecretKey,
            @Value("${" + ConfigKeys.STORAGE_BUCKET + "}") String storageBucket) {
        this.aiMode = AiMode.valueOf(aiMode.trim().toUpperCase());
        this.confidenceHigh = confidenceHigh;
        this.confidenceLow = confidenceLow;
        this.temperature = temperature;
        this.aggregation = aggregation;
        this.deadline = deadline;
        this.candidateLimit = candidateLimit;
        this.gradcamEnabled = gradcamEnabled;
        this.aiBaseUrl = aiBaseUrl;
        this.aiTimeout = aiTimeout;
        this.presignTtl = presignTtl;
        this.storageEndpoint = storageEndpoint;
        this.storageAccessKey = storageAccessKey;
        this.storageSecretKey = storageSecretKey;
        this.storageBucket = storageBucket;
    }

    public AiMode aiMode() {
        return aiMode;
    }

    public BigDecimal confidenceHigh() {
        return confidenceHigh;
    }

    public BigDecimal confidenceLow() {
        return confidenceLow;
    }

    public BigDecimal temperature() {
        return temperature;
    }

    public String aggregation() {
        return aggregation;
    }

    public Duration deadline() {
        return deadline;
    }

    public int candidateLimit() {
        return candidateLimit;
    }

    public boolean gradcamEnabled() {
        return gradcamEnabled;
    }

    public String aiBaseUrl() {
        return aiBaseUrl;
    }

    public Duration aiTimeout() {
        return aiTimeout;
    }

    public Duration presignTtl() {
        return presignTtl;
    }

    public String storageEndpoint() {
        return storageEndpoint;
    }

    public String storageAccessKey() {
        return storageAccessKey;
    }

    public String storageSecretKey() {
        return storageSecretKey;
    }

    public String storageBucket() {
        return storageBucket;
    }
}

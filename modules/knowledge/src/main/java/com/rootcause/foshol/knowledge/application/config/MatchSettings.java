package com.rootcause.foshol.knowledge.application.config;

import com.rootcause.foshol.common.contract.ConfigKeys;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MatchSettings {

    private final BigDecimal vectorThreshold;
    private final BigDecimal fuzzyThreshold;
    private final int maxSymptoms;
    private final int knnLimit;
    private final BigDecimal inconclusiveScoreMin;

    public MatchSettings(
            @Value("${" + ConfigKeys.KNOWLEDGE_MATCH_VECTOR_THRESHOLD + "}") BigDecimal vectorThreshold,
            @Value("${" + ConfigKeys.KNOWLEDGE_MATCH_FUZZY_THRESHOLD + "}") BigDecimal fuzzyThreshold,
            @Value("${" + ConfigKeys.KNOWLEDGE_MATCH_MAX_SYMPTOMS + "}") int maxSymptoms,
            @Value("${" + ConfigKeys.KNOWLEDGE_MATCH_KNN_LIMIT + "}") int knnLimit,
            @Value("${" + ConfigKeys.KNOWLEDGE_MATCH_INCONCLUSIVE_SCORE_MIN + "}")
                    BigDecimal inconclusiveScoreMin) {
        this.vectorThreshold = vectorThreshold;
        this.fuzzyThreshold = fuzzyThreshold;
        this.maxSymptoms = maxSymptoms;
        this.knnLimit = knnLimit;
        this.inconclusiveScoreMin = inconclusiveScoreMin;
    }

    public BigDecimal vectorThreshold() {
        return vectorThreshold;
    }

    public BigDecimal fuzzyThreshold() {
        return fuzzyThreshold;
    }

    public int maxSymptoms() {
        return maxSymptoms;
    }

    public int knnLimit() {
        return knnLimit;
    }

    public BigDecimal inconclusiveScoreMin() {
        return inconclusiveScoreMin;
    }
}

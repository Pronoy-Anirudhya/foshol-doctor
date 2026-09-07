package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.review.application.query.AdminStatsView;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReviewQueryPort {

    OfficerQueuePage findQueue(OfficerQueueQuery query);

    Optional<QueueTaskRow> findQueueRow(UUID reviewTaskId);

    AdminStatsView loadStats(Instant dayStartUtc, java.math.BigDecimal confidenceHigh, java.math.BigDecimal confidenceLow);

    record QueueTaskRow(
            UUID caseId,
            UUID reviewTaskId,
            String farmerName,
            String cropCode,
            String cropNameBn,
            String districtCode,
            String decisionPath,
            UUID topDiseaseId,
            String topDiseaseNameBn,
            java.math.BigDecimal topConfidence,
            int imageCount,
            boolean hasAudio,
            String analysisMode,
            String state,
            UUID officerId,
            boolean resubmission,
            short requeueCount,
            Instant submittedAt,
            Instant slaDueAt) {}
}

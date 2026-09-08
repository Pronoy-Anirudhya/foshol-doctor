package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.review.application.query.AdminCaseListCriteria;
import com.rootcause.foshol.review.application.query.AdminStatsView;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewQueryPort {

    OfficerQueuePage findQueue(OfficerQueueQuery query);

    Optional<QueueTaskRow> findQueueRow(UUID reviewTaskId);

    AdminStatsView loadStats(
            Instant dayStartUtc,
            Instant monthStartUtc,
            Instant yearStartUtc,
            BigDecimal confidenceHigh,
            BigDecimal confidenceLow,
            String districtCode);

    OfficerQueuePage findAdminCases(AdminCaseListCriteria criteria);

    List<com.rootcause.foshol.review.application.query.KpiWarningView> findOpenResolutionWarnings(
            UUID officerId, Instant now, java.time.Duration warnBefore);

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
            Instant slaDueAt,
            Instant assignmentDueAt,
            Instant resolutionDueAt) {}
}

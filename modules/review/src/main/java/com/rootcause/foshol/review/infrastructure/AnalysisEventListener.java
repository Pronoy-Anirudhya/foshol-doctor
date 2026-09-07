package com.rootcause.foshol.review.infrastructure;

import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.application.port.OfficerQueueProjection;
import com.rootcause.foshol.review.application.port.OfficerQueueProjectionPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.domain.ReviewTask;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AnalysisEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEventListener.class);

    private final ReviewTaskRepository tasks;
    private final OfficerQueueProjectionPort queue;
    private final CaseIntakeApi cases;
    private final FarmerLookupApi farmers;
    private final KnowledgeQueryApi knowledge;
    private final Clock clock;
    private final Duration sla;

    public AnalysisEventListener(
            ReviewTaskRepository tasks,
            OfficerQueueProjectionPort queue,
            CaseIntakeApi cases,
            FarmerLookupApi farmers,
            KnowledgeQueryApi knowledge,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_SLA + ":PT4H}") Duration sla) {
        this.tasks = tasks;
        this.queue = queue;
        this.cases = cases;
        this.farmers = farmers;
        this.knowledge = knowledge;
        this.clock = clock;
        this.sla = sla;
    }

    @ApplicationModuleListener
    @Transactional
    public void onCompleted(AnalysisCompleted event) {
        if (tasks.findByCaseId(event.caseId()).isPresent()) {
            return;
        }
        ReviewTask task = ReviewTask.createPending(
                Uuid7.create(),
                event.caseId(),
                event.top1Confidence(),
                event.occurredAt().plus(sla),
                clock.instant());
        tasks.save(task);
        CandidateView top = top(event.candidates());
        writeQueue(
                task,
                event.farmerId(),
                event.cropId(),
                event.decisionPath(),
                event.mode(),
                event.hasAudio(),
                event.imageCount(),
                top == null ? null : top.diseaseId(),
                top == null ? null : top.diseaseNameBn(),
                top == null ? null : top.confidence(),
                event.occurredAt(),
                event.correlationId());
    }

    @ApplicationModuleListener
    @Transactional
    public void onFailed(AnalysisFailed event) {
        if (tasks.findByCaseId(event.caseId()).isPresent()) {
            return;
        }
        ReviewTask task = ReviewTask.createPending(
                Uuid7.create(), event.caseId(), null, event.occurredAt().plus(sla), clock.instant());
        tasks.save(task);
        writeQueue(
                task,
                event.farmerId(),
                null,
                null,
                null,
                false,
                0,
                null,
                null,
                null,
                event.occurredAt(),
                event.correlationId());
    }

    private void writeQueue(
            ReviewTask task,
            UUID farmerId,
            UUID cropId,
            DecisionPath path,
            AiMode mode,
            boolean hasAudio,
            int imageCount,
            UUID topDiseaseId,
            String topDiseaseNameBn,
            BigDecimal topConfidence,
            Instant occurredAt,
            String correlationId) {
        CaseSummary summary = lookupCase(task.caseId(), correlationId).orElse(null);
        String farmerName = lookupFarmer(farmerId, correlationId);
        String cropCode = summary == null || summary.cropCode() == null ? "" : summary.cropCode();
        String cropNameBn = "";
        if (cropId != null) {
            try {
                cropNameBn = knowledge.findCropById(cropId).map(CropView::nameBn).orElse("");
                if (cropCode.isBlank()) {
                    cropCode = knowledge.findCropById(cropId).map(CropView::code).orElse("");
                }
            } catch (RuntimeException ex) {
                log.warn("KnowledgeQueryApi failed correlationId={} cropId={}", correlationId, cropId, ex);
                cropNameBn = "";
            }
        }
        String district = summary != null && summary.districtCode() != null ? summary.districtCode() : "";
        if (district.isBlank()) {
            try {
                district = farmers.findById(farmerId).map(FarmerView::districtCode).orElse("");
            } catch (RuntimeException ignored) {
                district = "";
            }
        }
        Instant submittedAt = summary != null && summary.submittedAt() != null ? summary.submittedAt() : occurredAt;
        boolean resubmission = summary != null && summary.parentCaseId() != null;
        if (summary != null && summary.images() != null) {
            imageCount = summary.images().size();
        }
        if (summary != null && summary.audio() != null) {
            hasAudio = true;
        }
        if (summary != null && summary.decisionPath() != null && path == null) {
            path = summary.decisionPath();
        }
        queue.insert(
                new OfficerQueueProjection(
                        task.caseId(),
                        task.id(),
                        clip(farmerName, 120),
                        clip(cropCode, 24),
                        clip(cropNameBn, 120),
                        clip(district, 8),
                        path,
                        topDiseaseId,
                        topDiseaseNameBn,
                        topConfidence,
                        imageCount,
                        hasAudio,
                        mode,
                        ReviewState.PENDING,
                        null,
                        resubmission,
                        submittedAt,
                        task.slaDueAt()),
                clock.instant());
    }

    private Optional<CaseSummary> lookupCase(UUID caseId, String correlationId) {
        try {
            return cases.findById(caseId);
        } catch (RuntimeException ex) {
            log.warn("CaseIntakeApi failed correlationId={} caseId={}", correlationId, caseId, ex);
            return Optional.empty();
        }
    }

    private String lookupFarmer(UUID farmerId, String correlationId) {
        try {
            return farmers.findById(farmerId).map(FarmerView::name).orElse("");
        } catch (RuntimeException ex) {
            log.warn("FarmerLookupApi failed correlationId={} farmerId={}", correlationId, farmerId, ex);
            return "";
        }
    }

    private static CandidateView top(List<CandidateView> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream().min(Comparator.comparingInt(CandidateView::rank)).orElse(null);
    }

    private static String clip(String value, int max) {
        String v = value == null ? "" : value;
        return v.length() <= max ? v : v.substring(0, max);
    }
}

package com.rootcause.foshol.review.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.rootcause.foshol.common.enums.AiMode;
import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.enums.CropQuantityUnit;
import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.common.enums.MetricsSource;
import com.rootcause.foshol.common.enums.ReviewState;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import com.rootcause.foshol.common.events.SymptomView;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewCaseDetailResponse(
        ReviewTaskHttp task,
        @JsonProperty("case") CaseDetailHttp caseDetail,
        AnalysisDetailHttp analysis,
        @JsonProperty("priorAdvisory") AdvisoryView priorAdvisory,
        @JsonUnwrapped ReviewTaskDetailView detail) {

    public static ReviewCaseDetailResponse from(ReviewTaskDetailView view, BigDecimal high, BigDecimal low) {
        return new ReviewCaseDetailResponse(
                ReviewTaskHttp.from(view),
                CaseDetailHttp.from(view),
                AnalysisDetailHttp.from(view, high, low),
                view.publishedAdvisory(),
                view);
    }

    public record ReviewTaskHttp(
            UUID taskId,
            UUID caseId,
            ReviewState state,
            UUID officerId,
            String officerName,
            Instant claimedAt,
            Instant claimExpiresAt,
            Instant slaDueAt,
            Instant assignmentDueAt,
            Instant resolutionDueAt,
            int requeueCount,
            int version) {

        static ReviewTaskHttp from(ReviewTaskDetailView view) {
            return new ReviewTaskHttp(
                    view.reviewTaskId(),
                    view.caseId(),
                    view.state(),
                    view.officerId(),
                    view.officerName(),
                    view.claimedAt(),
                    view.claimExpiresAt(),
                    view.slaDueAt(),
                    view.assignmentDueAt(),
                    view.resolutionDueAt(),
                    view.requeueCount(),
                    view.version());
        }
    }

    public record CaseDetailHttp(
            UUID caseId,
            UUID cropId,
            String cropNameBn,
            String cropNameEn,
            boolean cropNameEnFallback,
            CaseStatus status,
            DecisionPath decisionPath,
            String noteBn,
            UUID parentCaseId,
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit,
            MetricsSource metricsSource,
            List<CaseImageHttp> images,
            CaseAudioHttp audio,
            Instant submittedAt) {

        static CaseDetailHttp from(ReviewTaskDetailView view) {
            List<CaseImageRef> refs = view.images() == null ? List.of() : view.images();
            return new CaseDetailHttp(
                    view.caseId(),
                    view.cropId(),
                    view.cropNameBn(),
                    view.cropNameEn(),
                    view.cropNameEnFallback(),
                    view.caseStatus(),
                    view.decisionPath(),
                    view.noteBn(),
                    view.parentCaseId(),
                    view.fieldArea(),
                    view.fieldAreaUnit(),
                    view.cropQuantity(),
                    view.cropQuantityUnit(),
                    view.metricsSource(),
                    refs.stream().map(CaseImageHttp::from).toList(),
                    CaseAudioHttp.from(view.audio()),
                    view.submittedAt());
        }
    }

    public record CaseImageHttp(
            UUID imageId, int position, boolean primary, BigDecimal qualityScore, Integer width, Integer height) {

        static CaseImageHttp from(CaseImageRef ref) {
            return new CaseImageHttp(
                    ref.imageId(), ref.position(), ref.primary(), ref.qualityScore(), null, null);
        }
    }

    public record CaseAudioHttp(UUID audioId, int durationMs, String transcriptBn) {

        static CaseAudioHttp from(CaseAudioRef ref) {
            if (ref == null) {
                return null;
            }
            return new CaseAudioHttp(ref.audioId(), ref.durationMs(), ref.transcriptBn());
        }
    }

    public record AnalysisDetailHttp(
            UUID caseId,
            DecisionPath decisionPath,
            AiMode mode,
            BigDecimal top1Confidence,
            BigDecimal top2Confidence,
            BigDecimal margin,
            List<CandidateView> candidates,
            List<SymptomView> symptoms,
            String transcriptBn,
            BigDecimal asrConfidence,
            boolean hasGradcam,
            List<String> unmappedLabels,
            String visionModelId,
            String visionModelVersion,
            int latencyMs,
            String errorCode,
            ThresholdsHttp thresholds) {

        static AnalysisDetailHttp from(ReviewTaskDetailView view, BigDecimal high, BigDecimal low) {
            List<CandidateView> candidates = view.candidates() == null ? List.of() : view.candidates();
            List<SymptomView> symptoms = view.symptoms() == null ? List.of() : view.symptoms();
            List<String> unmapped = view.unmappedLabels() == null ? List.of() : view.unmappedLabels();
            return new AnalysisDetailHttp(
                    view.caseId(),
                    view.decisionPath(),
                    view.analysisMode(),
                    view.top1Confidence(),
                    view.top2Confidence(),
                    view.margin(),
                    candidates,
                    symptoms,
                    view.transcriptBn(),
                    view.asrConfidence(),
                    view.hasGradcam(),
                    unmapped,
                    view.visionModelId(),
                    view.visionModelVersion(),
                    view.latencyMs(),
                    view.errorCode(),
                    new ThresholdsHttp(high, low));
        }
    }

    public record ThresholdsHttp(BigDecimal high, BigDecimal low) {}
}

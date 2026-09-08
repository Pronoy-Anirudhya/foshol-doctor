package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.analysis.api.AnalysisApi;
import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.DoseCalculator;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.common.ReviewState;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.ComputedDoseView;
import com.rootcause.foshol.review.api.RemedyRefView;
import com.rootcause.foshol.review.application.ReviewDistrictGuard;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.ReviewQueryPort.QueueTaskRow;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.port.ReviewTaskRepository;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailQuery;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailView;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.domain.ReviewTask;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewTaskDetailQueryHandler implements QueryHandler<ReviewTaskDetailQuery, ReviewTaskDetailView> {

    @Override
    public Class<ReviewTaskDetailQuery> queryType() {
        return ReviewTaskDetailQuery.class;
    }

    private final ReviewQueryPort reads;
    private final ReviewTaskRepository tasks;
    private final AdvisoryRepository advisories;
    private final AnalysisApi analysisApi;
    private final CaseIntakeApi cases;
    private final KnowledgeQueryApi knowledge;
    private final OfficerLookupApi officers;
    private final ReviewDistrictGuard districtGuard;
    private final Clock clock;
    private final Duration claimTtl;

    public ReviewTaskDetailQueryHandler(
            ReviewQueryPort reads,
            ReviewTaskRepository tasks,
            AdvisoryRepository advisories,
            AnalysisApi analysisApi,
            CaseIntakeApi cases,
            KnowledgeQueryApi knowledge,
            OfficerLookupApi officers,
            ReviewDistrictGuard districtGuard,
            Clock clock,
            @Value("${" + ConfigKeys.REVIEW_CLAIM_TTL + ":PT15M}") Duration claimTtl) {
        this.reads = reads;
        this.tasks = tasks;
        this.advisories = advisories;
        this.analysisApi = analysisApi;
        this.cases = cases;
        this.knowledge = knowledge;
        this.officers = officers;
        this.districtGuard = districtGuard;
        this.clock = clock;
        this.claimTtl = claimTtl;
    }

    @Transactional(readOnly = true)
    @Override
    public ReviewTaskDetailView handle(ReviewTaskDetailQuery query) {
        QueueTaskRow row = reads.findQueueRow(query.taskId()).orElseThrow(ReviewException::taskNotFound);
        districtGuard.requireDistrict(query.callerId(), row.districtCode());
        ReviewTask task = tasks.findById(query.taskId()).orElseThrow(ReviewException::taskNotFound);
        CaseSummary summary = cases.findById(row.caseId()).orElse(null);
        AnalysisView analysis = analysisApi.findByCaseId(row.caseId()).orElse(null);
        UUID suggested = analysis == null || analysis.candidates() == null
                ? row.topDiseaseId()
                : analysis.candidates().stream()
                        .filter(c -> c.rank() == 1)
                        .map(CandidateView::diseaseId)
                        .findFirst()
                        .orElse(row.topDiseaseId());
        List<RemedyRefView> suggestedRemedies = suggested == null
                ? List.of()
                : knowledge.listActiveRemedies(suggested).stream()
                        .map(r -> toSuggestedRef(r, summary))
                        .toList();
        AdvisoryView published = advisories.findPublishedByCaseId(row.caseId())
                .map(a -> new AdvisoryView(
                        a.id(),
                        a.caseId(),
                        a.diseaseId(),
                        knowledge.findDiseaseById(a.diseaseId()).map(d -> d.nameBn()).orElse(""),
                        a.officerId(),
                        officers.findById(a.officerId()).map(o -> o.name()).orElse(""),
                        a.action(),
                        a.officerNoteBn(),
                        a.version(),
                        a.supersedesId(),
                        List.of(),
                        a.publishedAt()))
                .orElse(null);
        return new ReviewTaskDetailView(
                row.caseId(),
                row.reviewTaskId(),
                row.farmerName(),
                row.cropCode(),
                row.cropNameBn(),
                row.districtCode(),
                row.decisionPath() == null ? null : DecisionPath.valueOf(row.decisionPath()),
                row.topDiseaseId(),
                row.topDiseaseNameBn(),
                row.topConfidence(),
                row.imageCount(),
                row.hasAudio(),
                row.analysisMode() == null ? null : AiMode.valueOf(row.analysisMode()),
                ReviewState.valueOf(row.state()),
                row.officerId(),
                row.resubmission(),
                row.requeueCount(),
                row.submittedAt(),
                row.slaDueAt(),
                task.assignmentDueAt(),
                task.resolutionDueAt(),
                analysis == null ? null : analysis.top1Confidence(),
                analysis == null ? null : analysis.top2Confidence(),
                analysis == null ? null : analysis.margin(),
                analysis == null || analysis.candidates() == null ? List.of() : analysis.candidates(),
                analysis == null || analysis.symptoms() == null ? List.of() : analysis.symptoms(),
                analysis == null ? null : analysis.transcriptBn(),
                analysis == null ? null : analysis.asrConfidence(),
                analysis == null ? null : analysis.gradcamObjectKey(),
                summary == null || summary.images() == null ? List.of() : summary.images(),
                summary == null ? null : summary.audio(),
                summary == null ? null : summary.parentCaseId(),
                suggestedRemedies,
                task.officerId(),
                task.claimExpiresAt(claimTtl),
                published,
                task.version());
    }

    static RemedyRefView toSuggestedRef(RemedyView remedy, CaseSummary summary) {
        DoseCalculator.ComputedDose dose = summary == null
                ? null
                : DoseCalculator.compute(
                        summary.fieldArea(),
                        summary.fieldAreaUnit(),
                        remedy.rateAmount(),
                        remedy.rateUnit(),
                        remedy.rateBasis());
        ComputedDoseView computed = dose == null
                ? null
                : new ComputedDoseView(
                        dose.amount(), dose.unit(), dose.basis(), dose.fromArea(), dose.fromAreaUnit());
        return new RemedyRefView(
                remedy.id(),
                remedy.type(),
                remedy.titleBn(),
                remedy.stepsBn(),
                remedy.dosageBn(),
                remedy.phiDays(),
                remedy.sourceRef(),
                remedy.rateAmount(),
                remedy.rateUnit(),
                remedy.rateBasis(),
                remedy.rateNotesBn(),
                computed);
    }
}

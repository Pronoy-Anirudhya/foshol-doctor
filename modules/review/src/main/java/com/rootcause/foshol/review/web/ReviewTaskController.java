package com.rootcause.foshol.review.web;

import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RejectionView;
import com.rootcause.foshol.review.application.command.ApproveCaseCommand;
import com.rootcause.foshol.review.application.command.ApproveCaseCommandHandler;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskCommand;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskCommandHandler;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskResult;
import com.rootcause.foshol.review.application.command.RecordOfficerSymptomsCommand;
import com.rootcause.foshol.review.application.command.RecordOfficerSymptomsCommandHandler;
import com.rootcause.foshol.review.application.command.RejectCaseCommand;
import com.rootcause.foshol.review.application.command.RejectCaseCommandHandler;
import com.rootcause.foshol.review.application.command.ReleaseReviewTaskCommand;
import com.rootcause.foshol.review.application.command.ReleaseReviewTaskCommandHandler;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailQuery;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailQueryHandler;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailView;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review/tasks")
@PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
public class ReviewTaskController {

    private final ReviewTaskDetailQueryHandler detailQuery;
    private final ClaimReviewTaskCommandHandler claim;
    private final ReleaseReviewTaskCommandHandler release;
    private final ApproveCaseCommandHandler approve;
    private final RejectCaseCommandHandler reject;
    private final RecordOfficerSymptomsCommandHandler symptoms;

    public ReviewTaskController(
            ReviewTaskDetailQueryHandler detailQuery,
            ClaimReviewTaskCommandHandler claim,
            ReleaseReviewTaskCommandHandler release,
            ApproveCaseCommandHandler approve,
            RejectCaseCommandHandler reject,
            RecordOfficerSymptomsCommandHandler symptoms) {
        this.detailQuery = detailQuery;
        this.claim = claim;
        this.release = release;
        this.approve = approve;
        this.reject = reject;
        this.symptoms = symptoms;
    }

    @GetMapping("/{taskId}")
    public ReviewTaskDetailView get(@PathVariable UUID taskId) {
        return detailQuery.handle(new ReviewTaskDetailQuery(taskId));
    }

    @PostMapping("/{taskId}/claim")
    public ClaimReviewTaskResult claim(@PathVariable UUID taskId, Authentication authentication) {
        return claim.handle(new ClaimReviewTaskCommand(taskId, ReviewAuth.subjectId(authentication)));
    }

    @PostMapping("/{taskId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID taskId, Authentication authentication) {
        release.handle(new ReleaseReviewTaskCommand(taskId, ReviewAuth.subjectId(authentication)));
    }

    @PostMapping("/{taskId}/approve")
    public ResponseEntity<AdvisoryView> approve(
            @PathVariable UUID taskId,
            @RequestBody PublishAdvisoryRequest request,
            Authentication authentication) {
        AdvisoryView view = approve.handle(new ApproveCaseCommand(
                        taskId,
                        ReviewAuth.subjectId(authentication),
                        request.diseaseId(),
                        request.remedyIds() == null ? List.of() : request.remedyIds(),
                        request.officerNoteBn()))
                .advisory();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/cases/" + view.caseId() + "/advisory"))
                .body(view);
    }

    @PostMapping("/{taskId}/reject")
    public RejectionView reject(
            @PathVariable UUID taskId,
            @Valid @RequestBody RejectCaseRequest request,
            Authentication authentication) {
        return reject.handle(new RejectCaseCommand(
                taskId,
                ReviewAuth.subjectId(authentication),
                request.reasonCode(),
                request.messageBn()));
    }

    @PostMapping("/{taskId}/symptoms")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void symptoms(
            @PathVariable UUID taskId,
            @RequestBody RecordSymptomsRequest request,
            Authentication authentication) {
        symptoms.handle(new RecordOfficerSymptomsCommand(
                taskId,
                ReviewAuth.subjectId(authentication),
                request.symptomIds() == null ? List.of() : request.symptomIds()));
    }
}

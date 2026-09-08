package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RejectionView;
import com.rootcause.foshol.review.application.command.ApproveCaseCommand;
import com.rootcause.foshol.review.application.command.ApproveCaseResult;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskCommand;
import com.rootcause.foshol.review.application.command.ClaimReviewTaskResult;
import com.rootcause.foshol.review.application.command.RecordOfficerSymptomsCommand;
import com.rootcause.foshol.review.application.command.RejectCaseCommand;
import com.rootcause.foshol.review.application.command.ReleaseReviewTaskCommand;
import com.rootcause.foshol.review.application.query.ReviewTaskDetailQuery;
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

    private final QueryBus queries;
    private final CommandBus commands;

    public ReviewTaskController(QueryBus queries, CommandBus commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping("/{taskId}")
    public ReviewTaskDetailView get(@PathVariable UUID taskId, Authentication authentication) {
        return queries.handle(new ReviewTaskDetailQuery(taskId, ReviewAuth.subjectId(authentication)));
    }

    @PostMapping("/{taskId}/claim")
    public ClaimReviewTaskResult claim(@PathVariable UUID taskId, Authentication authentication) {
        return commands.handle(new ClaimReviewTaskCommand(taskId, ReviewAuth.subjectId(authentication)));
    }

    @PostMapping("/{taskId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID taskId, Authentication authentication) {
        commands.handle(new ReleaseReviewTaskCommand(taskId, ReviewAuth.subjectId(authentication)));
    }

    @PostMapping("/{taskId}/approve")
    public ResponseEntity<AdvisoryView> approve(
            @PathVariable UUID taskId,
            @RequestBody PublishAdvisoryRequest request,
            Authentication authentication) {
        ApproveCaseResult result = commands.handle(new ApproveCaseCommand(
                taskId,
                ReviewAuth.subjectId(authentication),
                request.diseaseId(),
                request.remedyIds() == null ? List.of() : request.remedyIds(),
                request.officerNoteBn()));
        AdvisoryView view = result.advisory();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/cases/" + view.caseId() + "/advisory"))
                .body(view);
    }

    @PostMapping("/{taskId}/reject")
    public RejectionView reject(
            @PathVariable UUID taskId,
            @Valid @RequestBody RejectCaseRequest request,
            Authentication authentication) {
        return commands.handle(new RejectCaseCommand(
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
        commands.handle(new RecordOfficerSymptomsCommand(
                taskId,
                ReviewAuth.subjectId(authentication),
                request.symptomIds() == null ? List.of() : request.symptomIds()));
    }
}

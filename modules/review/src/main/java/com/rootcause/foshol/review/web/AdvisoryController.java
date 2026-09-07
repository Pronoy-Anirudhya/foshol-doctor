package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.application.command.ApproveCaseResult;
import com.rootcause.foshol.review.application.command.ReviseAdvisoryCommand;
import com.rootcause.foshol.review.application.query.CaseAdvisoryQuery;
import com.rootcause.foshol.review.application.query.CaseAdvisoryResult;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdvisoryController {

    private final QueryBus queries;
    private final CommandBus commands;

    public AdvisoryController(QueryBus queries, CommandBus commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping("/api/v1/cases/{caseId}/advisory")
    @PreAuthorize("isAuthenticated()")
    public Object getCaseAdvisory(@PathVariable UUID caseId, Authentication authentication) {
        CaseAdvisoryResult result = queries.handle(new CaseAdvisoryQuery(caseId, Actors.from(authentication)));
        if (result.published() != null) {
            return result.published();
        }
        return result.rejection();
    }

    @GetMapping("/api/v1/cases/{caseId}/advisories")
    @PreAuthorize("isAuthenticated()")
    public List<AdvisoryView> history(@PathVariable UUID caseId, Authentication authentication) {
        CaseAdvisoryResult result = queries.handle(new CaseAdvisoryQuery(caseId, Actors.from(authentication)));
        return result.history();
    }

    @PostMapping("/api/v1/advisories/{advisoryId}/revise")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    public ResponseEntity<AdvisoryView> revise(
            @PathVariable UUID advisoryId,
            @RequestBody PublishAdvisoryRequest request,
            Authentication authentication) {
        ApproveCaseResult result = commands.handle(new ReviseAdvisoryCommand(
                advisoryId,
                ReviewAuth.subjectId(authentication),
                request.diseaseId(),
                request.remedyIds() == null ? List.of() : request.remedyIds(),
                request.officerNoteBn()));
        AdvisoryView view = result.advisory();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/cases/" + view.caseId() + "/advisory"))
                .body(view);
    }
}

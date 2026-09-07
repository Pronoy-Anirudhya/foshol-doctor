package com.rootcause.foshol.review.web;

import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.application.command.ReviseAdvisoryCommand;
import com.rootcause.foshol.review.application.command.ReviseAdvisoryCommandHandler;
import com.rootcause.foshol.review.application.query.CaseAdvisoryQuery;
import com.rootcause.foshol.review.application.query.CaseAdvisoryQueryHandler;
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

    private final CaseAdvisoryQueryHandler caseAdvisory;
    private final ReviseAdvisoryCommandHandler revise;

    public AdvisoryController(CaseAdvisoryQueryHandler caseAdvisory, ReviseAdvisoryCommandHandler revise) {
        this.caseAdvisory = caseAdvisory;
        this.revise = revise;
    }

    @GetMapping("/api/v1/cases/{caseId}/advisory")
    @PreAuthorize("isAuthenticated()")
    public Object getCaseAdvisory(@PathVariable UUID caseId, Authentication authentication) {
        var result = caseAdvisory.handle(new CaseAdvisoryQuery(caseId, Actors.from(authentication)));
        if (result.published() != null) {
            return result.published();
        }
        return result.rejection();
    }

    @GetMapping("/api/v1/cases/{caseId}/advisories")
    @PreAuthorize("isAuthenticated()")
    public List<AdvisoryView> history(@PathVariable UUID caseId, Authentication authentication) {
        return caseAdvisory.handle(new CaseAdvisoryQuery(caseId, Actors.from(authentication))).history();
    }

    @PostMapping("/api/v1/advisories/{advisoryId}/revise")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    public ResponseEntity<AdvisoryView> revise(
            @PathVariable UUID advisoryId,
            @RequestBody PublishAdvisoryRequest request,
            Authentication authentication) {
        AdvisoryView view = revise.handle(new ReviseAdvisoryCommand(
                        advisoryId,
                        ReviewAuth.subjectId(authentication),
                        request.diseaseId(),
                        request.remedyIds() == null ? List.of() : request.remedyIds(),
                        request.officerNoteBn()))
                .advisory();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/cases/" + view.caseId() + "/advisory"))
                .body(view);
    }
}

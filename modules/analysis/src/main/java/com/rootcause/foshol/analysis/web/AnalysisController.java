package com.rootcause.foshol.analysis.web;

import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.analysis.application.AnalysisSettings;
import com.rootcause.foshol.analysis.application.query.AnalysisDetailQuery;
import com.rootcause.foshol.analysis.application.query.GradcamLink;
import com.rootcause.foshol.analysis.application.query.GradcamLinkQuery;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.common.cqrs.QueryBus;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
@PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
public class AnalysisController {

    private final QueryBus queries;
    private final AnalysisSettings settings;

    public AnalysisController(QueryBus queries, AnalysisSettings settings) {
        this.queries = queries;
        this.settings = settings;
    }

    @GetMapping("/{caseId}/analysis")
    public ResponseEntity<Map<String, Object>> getAnalysis(@PathVariable UUID caseId, Authentication authentication) {
        Caller caller = Caller.from(authentication);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<AnalysisView> view =
                queries.handle(new AnalysisDetailQuery(caseId, caller.id(), caller.role()));
        return view.map(v -> ResponseEntity.ok(toBody(v))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{caseId}/gradcam")
    public ResponseEntity<Void> getGradcam(@PathVariable UUID caseId, Authentication authentication) {
        Caller caller = Caller.from(authentication);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<GradcamLink> link = queries.handle(new GradcamLinkQuery(caseId, caller.id(), caller.role()));
        return link.map(this::redirect).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toBody(AnalysisView view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseId", view.caseId());
        body.put("decisionPath", view.decisionPath().name());
        body.put("mode", view.mode().name());
        body.put("top1Confidence", view.top1Confidence());
        body.put("top2Confidence", view.top2Confidence());
        body.put("margin", view.margin());
        body.put("candidates", view.candidates());
        body.put("symptoms", view.symptoms());
        body.put("transcriptBn", view.transcriptBn());
        body.put("asrConfidence", view.asrConfidence());
        body.put("hasGradcam", view.gradcamObjectKey() != null);
        body.put("unmappedLabels", view.unmappedLabels());
        body.put("visionModelId", view.visionModelId());
        body.put("visionModelVersion", view.visionModelVersion());
        body.put("latencyMs", view.latencyMs());
        body.put("errorCode", view.errorCode());
        body.put("thresholds", Map.of("high", settings.confidenceHigh(), "low", settings.confidenceLow()));
        return body;
    }

    private ResponseEntity<Void> redirect(GradcamLink link) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(link.url())).build();
    }

    record Caller(UUID id, Role role) {
        static Caller from(Authentication authentication) {
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }
            UUID id = UUID.fromString(authentication.getName());
            Role role = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                    .map(Role::valueOf)
                    .findFirst()
                    .orElse(Role.FARMER);
            return new Caller(id, role);
        }
    }
}

package com.rootcause.foshol.intake.web;

import com.rootcause.foshol.common.CropQuantityUnit;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.FieldAreaUnit;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.command.SubmitCaseResult;
import com.rootcause.foshol.intake.application.query.CaseAudioUrlQuery;
import com.rootcause.foshol.intake.application.query.CaseDetailQuery;
import com.rootcause.foshol.intake.application.query.CaseDetailView;
import com.rootcause.foshol.intake.application.query.CaseImageUrlQuery;
import com.rootcause.foshol.intake.application.query.FarmerCaseListQuery;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.intake.application.query.FarmerCaseRow;
import com.rootcause.foshol.intake.application.query.PageResult;
import com.rootcause.foshol.intake.application.query.PresignedUrlView;
import com.rootcause.foshol.intake.infrastructure.WebIntakeAdapter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final WebIntakeAdapter intake;
    private final QueryBus queries;

    public CaseController(WebIntakeAdapter intake, QueryBus queries) {
        this.intake = intake;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<Map<String, Object>> create(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam UUID cropId,
            @RequestParam(required = false) String noteBn,
            @RequestParam(required = false) UUID parentCaseId,
            @RequestParam BigDecimal fieldArea,
            @RequestParam FieldAreaUnit fieldAreaUnit,
            @RequestParam(required = false) BigDecimal cropQuantity,
            @RequestParam(required = false) CropQuantityUnit cropQuantityUnit,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "audioDurationMs", required = false) Integer audioDurationMs)
            throws Exception {
        SubmitCaseResult result = intake.submitForHttp(MultipartCaseAssembler.assemble(
                UUID.fromString(authentication.getName()),
                cropId,
                noteBn,
                parentCaseId,
                fieldArea,
                fieldAreaUnit,
                cropQuantity,
                cropQuantityUnit,
                audioDurationMs,
                images,
                audio,
                parseIdempotencyKey(idempotencyKey)));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, "/api/v1/cases/" + result.caseId());
        if (result.replayed()) {
            headers.add("Idempotency-Replayed", "true");
        }
        return new ResponseEntity<>(acceptedBody(result), headers, HttpStatus.ACCEPTED);
    }

    @GetMapping
    @PreAuthorize("hasRole('FARMER')")
    public PageResult<FarmerCaseRow> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.handle(new FarmerCaseListQuery(UUID.fromString(authentication.getName()), page, size));
    }

    @GetMapping("/{caseId}")
    @PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
    public CaseDetailView get(Authentication authentication, @PathVariable UUID caseId) {
        return queries.handle(
                new CaseDetailQuery(caseId, UUID.fromString(authentication.getName()), roleOf(authentication)));
    }

    @GetMapping("/{caseId}/images/{imageId}/content")
    @PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
    public ResponseEntity<Void> imageContent(
            Authentication authentication,
            @PathVariable UUID caseId,
            @PathVariable UUID imageId,
            @RequestParam(defaultValue = "DERIVATIVE") String variant) {
        boolean derivative = !"ORIGINAL".equalsIgnoreCase(variant);
        PresignedUrlView view = queries.handle(new CaseImageUrlQuery(
                caseId, imageId, UUID.fromString(authentication.getName()), roleOf(authentication), derivative));
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, view.url()).build();
    }

    @GetMapping("/{caseId}/images/{imageId}/url")
    @PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
    public PresignedUrlView imageUrl(
            Authentication authentication,
            @PathVariable UUID caseId,
            @PathVariable UUID imageId,
            @RequestParam(defaultValue = "original") String variant) {
        boolean derivative = "derivative".equalsIgnoreCase(variant);
        return queries.handle(new CaseImageUrlQuery(
                caseId, imageId, UUID.fromString(authentication.getName()), roleOf(authentication), derivative));
    }

    @GetMapping("/{caseId}/audio/content")
    @PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
    public ResponseEntity<Void> audioContent(Authentication authentication, @PathVariable UUID caseId) {
        PresignedUrlView view = queries.handle(
                new CaseAudioUrlQuery(caseId, UUID.fromString(authentication.getName()), roleOf(authentication)));
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, view.url()).build();
    }

    @GetMapping("/{caseId}/audio/url")
    @PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
    public PresignedUrlView audioUrl(Authentication authentication, @PathVariable UUID caseId) {
        return queries.handle(
                new CaseAudioUrlQuery(caseId, UUID.fromString(authentication.getName()), roleOf(authentication)));
    }

    static Role roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .map(Role::valueOf)
                .findFirst()
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_FORBIDDEN, 403, "Role is required."));
    }

    private static UUID parseIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IntakeException(ErrorCodes.ERR_IDEMPOTENCY_KEY_MISSING, 400, "Idempotency-Key is required.");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IntakeException(
                    ErrorCodes.ERR_IDEMPOTENCY_KEY_INVALID, 400, "Idempotency-Key must be a UUID.");
        }
    }

    private static Map<String, Object> acceptedBody(SubmitCaseResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseId", result.caseId());
        body.put("status", "SUBMITTED");
        String marker = "\"submittedAt\":\"";
        int start = result.body().indexOf(marker);
        if (start >= 0) {
            start += marker.length();
            int end = result.body().indexOf('"', start);
            if (end > start) {
                body.put("submittedAt", result.body().substring(start, end));
            }
        }
        return body;
    }
}

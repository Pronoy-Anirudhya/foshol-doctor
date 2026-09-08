package com.rootcause.foshol.notification.web;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.notification.infrastructure.sse.SseSubscriptionRegistry;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class StreamController {

    private final SseSubscriptionRegistry registry;
    private final OfficerLookupApi officers;
    private final boolean enabled;
    private final Duration timeout;

    public StreamController(
            SseSubscriptionRegistry registry,
            OfficerLookupApi officers,
            @Value("${" + ConfigKeys.CHANNELS_SSE_ENABLED + ":true}") boolean enabled,
            @Value("${" + ConfigKeys.CHANNELS_SSE_TIMEOUT + ":PT30M}") Duration timeout) {
        this.registry = registry;
        this.officers = officers;
        this.enabled = enabled;
        this.timeout = timeout;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(
            Authentication authentication,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        UUID subject = UUID.fromString(authentication.getName());
        Role role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .map(Role::valueOf)
                .findFirst()
                .orElseThrow();
        String district = "";
        if (role == Role.OFFICER || role == Role.ADMIN) {
            district = officers.findById(subject).map(o -> o.districtCode()).orElse("");
        }
        return registry.subscribe(subject, role, district, lastEventId, timeout);
    }
}

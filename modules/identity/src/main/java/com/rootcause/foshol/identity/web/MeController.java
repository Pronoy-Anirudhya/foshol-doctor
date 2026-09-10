package com.rootcause.foshol.identity.web;

import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.identity.application.query.MeQuery;
import com.rootcause.foshol.identity.application.query.MeView;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final QueryBus queries;

    public MeController(QueryBus queries) {
        this.queries = queries;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('FARMER','OFFICER','ADMIN')")
    public MeView me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(Role.AUTHORITY_PREFIX))
                .map(a -> Role.fromAuthority(a).name())
                .findFirst()
                .orElseThrow();
        return queries.handle(new MeQuery(UUID.fromString(authentication.getName()), role));
    }
}

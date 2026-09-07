package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.Role;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

final class ReviewAuth {

    private ReviewAuth() {}

    static UUID subjectId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    static Role role(Authentication authentication) {
        String raw = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElseThrow();
        return Role.valueOf(raw);
    }
}

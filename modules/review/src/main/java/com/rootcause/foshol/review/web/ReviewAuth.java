package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.enums.Role;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

final class ReviewAuth {

    private ReviewAuth() {}

    static UUID subjectId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    static Role role(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(Role.AUTHORITY_PREFIX))
                .map(Role::fromAuthority)
                .findFirst()
                .orElseThrow();
    }
}

package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.review.application.Actor;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class Actors {

    private Actors() {}

    public static Actor from(Authentication authentication) {
        UUID id = UUID.fromString(authentication.getName());
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElseThrow();
        return new Actor(id, Role.valueOf(role));
    }
}

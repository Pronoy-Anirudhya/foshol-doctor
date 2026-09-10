package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.review.application.command.Actor;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class Actors {

    private Actors() {}

    public static Actor from(Authentication authentication) {
        UUID id = UUID.fromString(authentication.getName());
        Role role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(Role.AUTHORITY_PREFIX))
                .map(Role::fromAuthority)
                .findFirst()
                .orElseThrow();
        return new Actor(id, role);
    }
}

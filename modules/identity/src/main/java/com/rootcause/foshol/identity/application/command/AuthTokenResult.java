package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.Role;
import java.time.Instant;
import java.util.UUID;

public record AuthTokenResult(
        String token,
        Instant expiresAt,
        Role role,
        UUID subjectId,
        String name,
        String districtCode,
        String preferredLanguage,
        String username) {}

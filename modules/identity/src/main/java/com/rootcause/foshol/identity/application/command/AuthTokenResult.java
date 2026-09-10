package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.enums.Role;
import java.time.Instant;
import java.util.UUID;

public record AuthTokenResult(
        String token,
        Instant expiresAt,
        Role role,
        UUID subjectId,
        String name,
        String districtCode,
        String divisionCode,
        String districtNameBn,
        String districtNameEn,
        String divisionNameBn,
        String divisionNameEn,
        String preferredLanguage,
        String username) {}

package com.rootcause.foshol.identity.application.query;

import java.util.UUID;

public record MeView(
        UUID id,
        String role,
        String name,
        String districtCode,
        String divisionCode,
        String districtNameBn,
        String districtNameEn,
        String divisionNameBn,
        String divisionNameEn,
        String preferredLanguage,
        String username) {}

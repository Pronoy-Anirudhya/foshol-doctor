package com.rootcause.foshol.identity.web;

import java.util.UUID;

public record PrincipalResponse(
        UUID id,
        String name,
        String role,
        String districtCode,
        String divisionCode,
        String districtNameBn,
        String districtNameEn,
        String divisionNameBn,
        String divisionNameEn,
        String preferredLanguage,
        String username) {}

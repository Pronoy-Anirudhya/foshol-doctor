package com.rootcause.foshol.identity.application.query;

import java.time.Instant;
import java.util.UUID;

public record FarmerRecord(
        UUID id,
        String name,
        String divisionCode,
        String districtCode,
        String divisionNameBn,
        String divisionNameEn,
        String districtNameBn,
        String districtNameEn,
        String preferredLanguage,
        Instant createdAt,
        UUID registeredByOfficerId,
        String registeredByName,
        String source) {}

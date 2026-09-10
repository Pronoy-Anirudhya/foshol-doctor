package com.rootcause.foshol.identity.application.port;

import java.time.Instant;
import java.util.UUID;

public record FarmerDirectorySnapshot(
        UUID id,
        String name,
        String districtCode,
        String divisionCode,
        String preferredLanguage,
        Instant createdAt,
        UUID registeredBy,
        String registrationSource) {}

package com.rootcause.foshol.identity.application.port;

import java.time.Instant;
import java.util.UUID;

public record FarmerSnapshot(
        UUID id,
        String name,
        String phoneHash,
        byte[] phoneEnc,
        String districtCode,
        String divisionCode,
        String preferredLanguage,
        UUID registeredBy,
        String registrationSource,
        Instant createdAt) {}

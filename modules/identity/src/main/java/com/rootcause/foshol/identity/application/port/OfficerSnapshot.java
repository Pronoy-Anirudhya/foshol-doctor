package com.rootcause.foshol.identity.application.port;

import java.util.UUID;

public record OfficerSnapshot(
        UUID id,
        String name,
        String username,
        String passwordHash,
        boolean active,
        String role,
        String districtCode,
        String divisionCode) {}

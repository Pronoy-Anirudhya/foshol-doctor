package com.rootcause.foshol.identity.application.port;

import java.util.UUID;

public record OfficerReadSnapshot(
        UUID id, String name, String username, String role, String districtCode, String divisionCode) {}

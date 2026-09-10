package com.rootcause.foshol.identity.application.port;

import java.util.UUID;

public record FarmerReadSnapshot(
        UUID id, String name, String districtCode, String divisionCode, String preferredLanguage) {}

package com.rootcause.foshol.identity.api;

import java.util.UUID;

public record FarmerView(
        UUID id, String name, String districtCode, String preferredLanguage, String divisionCode) {}

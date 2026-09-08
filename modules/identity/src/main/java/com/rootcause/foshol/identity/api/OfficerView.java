package com.rootcause.foshol.identity.api;

import java.util.UUID;

public record OfficerView(
        UUID id, String name, String districtCode, String role, boolean active, String divisionCode) {}

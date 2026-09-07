package com.rootcause.foshol.identity.web;

import java.util.UUID;

public record PrincipalResponse(
        UUID id, String name, String role, String districtCode, String preferredLanguage, String username) {}

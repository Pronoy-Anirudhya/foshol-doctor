package com.rootcause.foshol.identity.web;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, PrincipalResponse principal) {}

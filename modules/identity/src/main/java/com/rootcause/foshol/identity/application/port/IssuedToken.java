package com.rootcause.foshol.identity.application.port;

import java.time.Instant;

public record IssuedToken(String compact, Instant expiresAt) {}

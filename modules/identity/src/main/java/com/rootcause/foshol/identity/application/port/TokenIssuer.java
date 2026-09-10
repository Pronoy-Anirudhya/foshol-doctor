package com.rootcause.foshol.identity.application.port;

import com.rootcause.foshol.common.enums.Role;
import java.time.Instant;
import java.util.UUID;

public interface TokenIssuer {

    IssuedToken issue(UUID subjectId, Role role, Instant now);
}

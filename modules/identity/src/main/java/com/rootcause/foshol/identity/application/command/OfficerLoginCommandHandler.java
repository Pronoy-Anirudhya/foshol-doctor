package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerEntity;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.JwtService;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficerLoginCommandHandler {

    private static final String DUMMY_HASH = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    private final FieldOfficerJpaRepository officers;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public OfficerLoginCommandHandler(
            FieldOfficerJpaRepository officers,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock) {
        this.officers = officers;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthTokenResult handle(OfficerLoginCommand command) {
        FieldOfficerEntity officer = officers.findByUsername(command.username()).orElse(null);
        if (officer == null) {
            passwordEncoder.matches(command.password(), DUMMY_HASH);
            throw new IdentityException(ErrorCodes.ERR_INVALID_CREDENTIALS, 401, "Credentials are not valid.");
        }
        if (!passwordEncoder.matches(command.password(), officer.getPasswordHash())) {
            throw new IdentityException(ErrorCodes.ERR_INVALID_CREDENTIALS, 401, "Credentials are not valid.");
        }
        if (!officer.isActive()) {
            throw new IdentityException(ErrorCodes.ERR_ACCOUNT_INACTIVE, 401, "This account is inactive.");
        }
        Role role = Role.valueOf(officer.getRole());
        JwtService.IssuedToken token = jwtService.issue(officer.getId(), role, clock.instant());
        return new AuthTokenResult(
                token.compact(),
                token.expiresAt(),
                role,
                officer.getId(),
                officer.getName(),
                officer.getDistrictCode(),
                null,
                officer.getUsername());
    }
}

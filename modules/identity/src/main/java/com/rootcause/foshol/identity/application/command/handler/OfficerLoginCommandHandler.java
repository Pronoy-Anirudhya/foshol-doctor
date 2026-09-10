package com.rootcause.foshol.identity.application.command.handler;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.identity.application.command.AuthTokenResult;
import com.rootcause.foshol.identity.application.command.OfficerLoginCommand;
import com.rootcause.foshol.identity.application.port.GeoLabelPort;
import com.rootcause.foshol.identity.application.port.GeoLabels;
import com.rootcause.foshol.identity.application.port.IssuedToken;
import com.rootcause.foshol.identity.application.port.OfficerSnapshot;
import com.rootcause.foshol.identity.application.port.OfficerStore;
import com.rootcause.foshol.identity.application.port.TokenIssuer;
import com.rootcause.foshol.identity.domain.IdentityException;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficerLoginCommandHandler implements CommandHandler<OfficerLoginCommand, AuthTokenResult> {

    @Override
    public Class<OfficerLoginCommand> commandType() {
        return OfficerLoginCommand.class;
    }

    private static final String DUMMY_HASH = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    private final OfficerStore officers;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokens;
    private final GeoLabelPort geoLabels;
    private final Clock clock;

    public OfficerLoginCommandHandler(
            OfficerStore officers,
            PasswordEncoder passwordEncoder,
            TokenIssuer tokens,
            GeoLabelPort geoLabels,
            Clock clock) {
        this.officers = officers;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.geoLabels = geoLabels;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public AuthTokenResult handle(OfficerLoginCommand command) {
        OfficerSnapshot officer = officers.findByUsername(command.username()).orElse(null);
        if (officer == null) {
            passwordEncoder.matches(command.password(), DUMMY_HASH);
            throw new IdentityException(ErrorCodes.ERR_INVALID_CREDENTIALS, 401, "Credentials are not valid.");
        }
        if (!passwordEncoder.matches(command.password(), officer.passwordHash())) {
            throw new IdentityException(ErrorCodes.ERR_INVALID_CREDENTIALS, 401, "Credentials are not valid.");
        }
        if (!officer.active()) {
            throw new IdentityException(ErrorCodes.ERR_ACCOUNT_INACTIVE, 401, "This account is inactive.");
        }
        Role role = Role.valueOf(officer.role());
        IssuedToken token = tokens.issue(officer.id(), role, clock.instant());
        GeoLabels geo = geoLabels.forDistrict(officer.districtCode(), officer.divisionCode());
        return new AuthTokenResult(
                token.compact(),
                token.expiresAt(),
                role,
                officer.id(),
                officer.name(),
                officer.districtCode(),
                officer.divisionCode(),
                geo.districtNameBn(),
                geo.districtNameEn(),
                geo.divisionNameBn(),
                geo.divisionNameEn(),
                null,
                officer.username());
    }
}

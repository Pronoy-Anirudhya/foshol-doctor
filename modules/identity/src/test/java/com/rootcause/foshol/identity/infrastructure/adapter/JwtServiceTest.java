package com.rootcause.foshol.identity.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.identity.application.port.IssuedToken;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void issuesHs256TokenWithRequiredClaims() {
        JwtService jwt = new JwtService("foshol-doctor", Duration.ofHours(8), "local-dev-jwt-secret-must-be-32chars");
        jwt.validateSecret();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID sub = UUID.fromString("01800000-0000-7000-8000-000000000201");
        IssuedToken issued = jwt.issue(sub, Role.FARMER, now);
        var decoded = jwt.verify(issued.compact());
        assertThat(decoded.getSubject()).isEqualTo(sub.toString());
        assertThat(decoded.getIssuer()).isEqualTo("foshol-doctor");
        assertThat(decoded.getClaim("role").asString()).isEqualTo("FARMER");
        assertThat(decoded.getExpiresAtAsInstant()).isEqualTo(issued.expiresAt());
    }
}

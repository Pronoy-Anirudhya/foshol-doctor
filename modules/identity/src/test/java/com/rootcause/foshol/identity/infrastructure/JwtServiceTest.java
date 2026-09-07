package com.rootcause.foshol.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void issuesHs256TokenWithRequiredClaims() {
        JwtService jwt = new JwtService("foshol-doctor", Duration.ofHours(8), "local-dev-jwt-secret-must-be-32chars");
        jwt.validateSecret();
        Instant now = Instant.parse("2026-09-07T10:00:00Z");
        UUID sub = UUID.fromString("01800000-0000-7000-8000-000000000201");
        JwtService.IssuedToken issued = jwt.issue(sub, Role.FARMER, now);
        var decoded = jwt.verify(issued.compact());
        assertThat(decoded.getSubject()).isEqualTo(sub.toString());
        assertThat(decoded.getIssuer()).isEqualTo("foshol-doctor");
        assertThat(decoded.getClaim("role").asString()).isEqualTo("FARMER");
        assertThat(decoded.getExpiresAtAsInstant()).isEqualTo(now.plus(Duration.ofHours(8)));
    }
}

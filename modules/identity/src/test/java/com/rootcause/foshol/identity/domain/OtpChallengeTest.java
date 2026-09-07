package com.rootcause.foshol.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OtpChallengeTest {

    @Test
    void isVerifiableUntilConsumedExpiredOrMaxAttempts() {
        Instant now = Instant.parse("2026-09-07T10:00:00Z");
        OtpChallenge challenge = new OtpChallenge(
                UUID.randomUUID(), "hash", "code", 0, now.plusSeconds(60), null, now);
        assertThat(challenge.isVerifiable(now, 5)).isTrue();
        challenge.incrementAttempts();
        challenge.incrementAttempts();
        challenge.incrementAttempts();
        challenge.incrementAttempts();
        challenge.incrementAttempts();
        assertThat(challenge.isVerifiable(now, 5)).isFalse();
    }

    @Test
    void consumeIsIrreversible() {
        Instant now = Instant.parse("2026-09-07T10:00:00Z");
        OtpChallenge challenge = new OtpChallenge(
                UUID.randomUUID(), "hash", "code", 0, now.plusSeconds(60), null, now);
        challenge.consume(now);
        assertThat(challenge.consumedAt()).isEqualTo(now);
        assertThat(challenge.isVerifiable(now, 5)).isFalse();
    }
}

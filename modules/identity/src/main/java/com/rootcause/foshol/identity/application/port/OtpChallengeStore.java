package com.rootcause.foshol.identity.application.port;

import com.rootcause.foshol.identity.domain.OtpChallenge;
import java.time.Instant;
import java.util.Optional;

public interface OtpChallengeStore {

    long countCreatedAfter(String phoneHash, Instant createdAfter);

    void consumeOpen(String phoneHash, Instant now);

    void save(OtpChallenge challenge);

    Optional<OtpChallenge> findLatestOpen(String phoneHash);
}

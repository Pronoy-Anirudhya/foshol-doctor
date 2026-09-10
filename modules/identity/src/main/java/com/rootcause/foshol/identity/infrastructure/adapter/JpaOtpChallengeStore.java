package com.rootcause.foshol.identity.infrastructure.adapter;

import com.rootcause.foshol.identity.application.port.OtpChallengeStore;
import com.rootcause.foshol.identity.domain.OtpChallenge;
import com.rootcause.foshol.identity.infrastructure.persistence.OtpChallengeEntity;
import com.rootcause.foshol.identity.infrastructure.persistence.OtpChallengeJpaRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOtpChallengeStore implements OtpChallengeStore {

    private final OtpChallengeJpaRepository challenges;

    public JpaOtpChallengeStore(OtpChallengeJpaRepository challenges) {
        this.challenges = challenges;
    }

    @Override
    public long countCreatedAfter(String phoneHash, Instant createdAfter) {
        return challenges.countByPhoneHashAndCreatedAtAfter(phoneHash, createdAfter);
    }

    @Override
    public void consumeOpen(String phoneHash, Instant now) {
        challenges.consumeOpenChallenges(phoneHash, now);
    }

    @Override
    public void save(OtpChallenge challenge) {
        challenges.save(toEntity(challenge));
    }

    @Override
    public Optional<OtpChallenge> findLatestOpen(String phoneHash) {
        return challenges
                .findFirstByPhoneHashAndConsumedAtIsNullOrderByCreatedAtDesc(phoneHash)
                .map(JpaOtpChallengeStore::toDomain);
    }

    private static OtpChallenge toDomain(OtpChallengeEntity row) {
        return new OtpChallenge(
                row.getId(),
                row.getPhoneHash(),
                row.getCodeHash(),
                row.getAttempts(),
                row.getExpiresAt(),
                row.getConsumedAt(),
                row.getCreatedAt());
    }

    private static OtpChallengeEntity toEntity(OtpChallenge challenge) {
        return new OtpChallengeEntity(
                challenge.id(),
                challenge.phoneHash(),
                challenge.codeHash(),
                (short) challenge.attempts(),
                challenge.expiresAt(),
                challenge.consumedAt(),
                challenge.createdAt());
    }
}

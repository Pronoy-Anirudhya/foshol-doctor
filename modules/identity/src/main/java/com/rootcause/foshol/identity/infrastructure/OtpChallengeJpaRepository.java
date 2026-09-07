package com.rootcause.foshol.identity.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeJpaRepository extends JpaRepository<OtpChallengeEntity, UUID> {

    Optional<OtpChallengeEntity> findFirstByPhoneHashAndConsumedAtIsNullOrderByCreatedAtDesc(String phoneHash);

    long countByPhoneHashAndCreatedAtAfter(String phoneHash, Instant createdAfter);

    @Modifying
    @Query("update OtpChallengeEntity c set c.consumedAt = :now where c.phoneHash = :phoneHash and c.consumedAt is null")
    void consumeOpenChallenges(@Param("phoneHash") String phoneHash, @Param("now") Instant now);
}

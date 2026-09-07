package com.rootcause.foshol.intake.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    @Modifying
    @Query("delete from IdempotencyKeyEntity k where k.expiresAt < :now")
    void deleteByExpiresAtBefore(@Param("now") Instant now);
}

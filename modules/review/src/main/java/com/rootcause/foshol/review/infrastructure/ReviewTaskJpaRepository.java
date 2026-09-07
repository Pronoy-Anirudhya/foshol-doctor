package com.rootcause.foshol.review.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewTaskJpaRepository extends JpaRepository<ReviewTaskEntity, UUID> {

    boolean existsByCaseId(UUID caseId);

    Optional<ReviewTaskEntity> findByCaseId(UUID caseId);

    @Query(
            value =
                    """
                    SELECT * FROM review_task
                    WHERE state = 'CLAIMED' AND claimed_at < :cutoff
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<ReviewTaskEntity> lockExpiredClaims(@Param("cutoff") Instant cutoff);
}

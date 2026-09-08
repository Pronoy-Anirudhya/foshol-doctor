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

    @Query(
            value =
                    """
                    SELECT t.* FROM review_task t
                    WHERE t.state = 'PENDING' AND t.assignment_due_at <= :now
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<ReviewTaskEntity> lockOverdueAssignments(@Param("now") Instant now);

    @Query(
            value =
                    """
                    SELECT t.* FROM review_task t
                    WHERE t.state = 'CLAIMED' AND t.resolution_due_at IS NOT NULL AND t.resolution_due_at <= :now
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<ReviewTaskEntity> lockOverdueResolutions(@Param("now") Instant now);

    @Query(
            value =
                    """
                    SELECT t.* FROM review_task t
                    WHERE t.state = 'CLAIMED'
                      AND t.resolution_due_at IS NOT NULL
                      AND t.kpi_warn_emitted_at IS NULL
                      AND t.resolution_due_at > :now
                      AND t.resolution_due_at <= :warnCutoff
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<ReviewTaskEntity> lockResolutionWarnings(
            @Param("warnCutoff") Instant warnCutoff, @Param("now") Instant now);
}

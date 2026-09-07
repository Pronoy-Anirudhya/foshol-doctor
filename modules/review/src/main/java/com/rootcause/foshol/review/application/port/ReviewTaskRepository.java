package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.review.domain.ReviewTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewTaskRepository {

    Optional<ReviewTask> findById(UUID id);

    Optional<ReviewTask> findByCaseId(UUID caseId);

    ReviewTask save(ReviewTask task);

    List<ReviewTask> lockExpiredClaims(Instant cutoff);

    long count();
}

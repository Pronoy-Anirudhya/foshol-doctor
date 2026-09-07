package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.review.domain.Advisory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdvisoryRepository {

    void insert(Advisory advisory);

    Optional<Advisory> findById(UUID id);

    Optional<Advisory> findPublishedByCaseId(UUID caseId);

    List<Advisory> findHistoryByCaseId(UUID caseId);
}

package com.rootcause.foshol.review.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewSubmissionApi {

    Optional<AdvisoryView> findPublishedAdvisory(UUID caseId);

    List<AdvisoryView> findAdvisoryHistory(UUID caseId);

    Optional<RejectionView> findRejection(UUID caseId);
}

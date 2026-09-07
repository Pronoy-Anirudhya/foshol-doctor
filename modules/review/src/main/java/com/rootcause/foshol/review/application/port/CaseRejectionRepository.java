package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.review.domain.CaseRejection;
import java.util.Optional;
import java.util.UUID;

public interface CaseRejectionRepository {

    void insert(CaseRejection rejection);

    Optional<CaseRejection> findByCaseId(UUID caseId);
}

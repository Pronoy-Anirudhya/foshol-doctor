package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.common.enums.ReviewState;
import java.time.Instant;
import java.util.UUID;

public interface OfficerQueueProjectionPort {

    void insert(OfficerQueueProjection row, Instant now);

    void updateState(UUID caseId, ReviewState state, UUID officerId, Instant now);

    void updateKpiClocks(UUID caseId, Instant assignmentDueAt, Instant resolutionDueAt, Instant now);

    boolean existsByCaseId(UUID caseId);
}

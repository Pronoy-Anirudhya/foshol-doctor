package com.rootcause.foshol.analysis.application.port;

import com.rootcause.foshol.analysis.api.AnalysisView;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisReadRepository {

    Optional<AnalysisView> findByCaseId(UUID caseId);

    Optional<String> findGradcamObjectKey(UUID caseId);

    Optional<UUID> findPrimaryGradcamImageId(UUID caseId);
}

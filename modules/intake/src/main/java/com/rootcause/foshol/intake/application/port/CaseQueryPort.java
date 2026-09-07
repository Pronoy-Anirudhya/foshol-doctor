package com.rootcause.foshol.intake.application.port;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.intake.application.query.CaseDetailView;
import com.rootcause.foshol.intake.application.query.FarmerCaseRow;
import com.rootcause.foshol.intake.application.query.PageResult;
import com.rootcause.foshol.intake.api.CaseSummary;
import java.util.Optional;
import java.util.UUID;

public interface CaseQueryPort {

    Optional<CaseSummary> findSummary(UUID caseId);

    Optional<CaseDetailView> findDetail(UUID caseId);

    PageResult<FarmerCaseRow> listHistory(UUID farmerId, int page, int size);

    Optional<ImageLocator> findImage(UUID caseId, UUID imageId);

    Optional<AudioLocator> findAudio(UUID caseId);

    Optional<CaseStatus> findStatus(UUID caseId);

    Optional<UUID> findFarmerId(UUID caseId);

    record ImageLocator(UUID caseId, UUID farmerId, String objectKey, String derivativeObjectKey) {}

    record AudioLocator(UUID caseId, UUID farmerId, String objectKey) {}
}

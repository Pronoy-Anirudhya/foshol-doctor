package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.port.ImageStorePort;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseImageUrlQueryHandler {

    private final CaseQueryPort queries;
    private final ImageStorePort store;
    private final Duration presignTtl;
    private final Clock clock;

    public CaseImageUrlQueryHandler(
            CaseQueryPort queries,
            ImageStorePort store,
            Clock clock,
            @Value("${" + ConfigKeys.STORAGE_PRESIGN_TTL + "}") Duration presignTtl) {
        this.queries = queries;
        this.store = store;
        this.clock = clock;
        this.presignTtl = presignTtl;
    }

    @Transactional(readOnly = true)
    public PresignedUrlView handle(CaseImageUrlQuery query) {
        java.util.UUID owner = queries
                .findFarmerId(query.caseId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found."));
        if (query.callerRole() != Role.OFFICER
                && query.callerRole() != Role.ADMIN
                && !owner.equals(query.callerId())) {
            throw new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found.");
        }
        CaseQueryPort.ImageLocator locator = queries
                .findImage(query.caseId(), query.imageId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_IMAGE_NOT_FOUND, 404, "Image was not found."));
        String key = query.derivative() && locator.derivativeObjectKey() != null
                ? locator.derivativeObjectKey()
                : locator.objectKey();
        String url = store.presign(key, presignTtl);
        return new PresignedUrlView(url, clock.instant().plus(presignTtl));
    }
}

package com.rootcause.foshol.intake.application.query.handler;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.intake.domain.IntakeException;
import com.rootcause.foshol.intake.application.query.StaffRegionAccess;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.port.ImageStorePort;
import com.rootcause.foshol.intake.application.query.CaseAudioUrlQuery;
import com.rootcause.foshol.intake.application.query.PresignedUrlView;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseAudioUrlQueryHandler implements QueryHandler<CaseAudioUrlQuery, PresignedUrlView> {

    @Override
    public Class<CaseAudioUrlQuery> queryType() {
        return CaseAudioUrlQuery.class;
    }

    private final CaseQueryPort queries;
    private final ImageStorePort store;
    private final StaffRegionAccess staffRegion;
    private final Duration presignTtl;
    private final Clock clock;

    public CaseAudioUrlQueryHandler(
            CaseQueryPort queries,
            ImageStorePort store,
            StaffRegionAccess staffRegion,
            Clock clock,
            @Value("${" + ConfigKeys.STORAGE_PRESIGN_TTL + "}") Duration presignTtl) {
        this.queries = queries;
        this.store = store;
        this.staffRegion = staffRegion;
        this.clock = clock;
        this.presignTtl = presignTtl;
    }

    @Transactional(readOnly = true)
    @Override
    public PresignedUrlView handle(CaseAudioUrlQuery query) {
        java.util.UUID owner = queries
                .findFarmerId(query.caseId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found."));
        CaseQueryPort.AudioLocator locator = queries
                .findAudio(query.caseId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_AUDIO_NOT_FOUND, 404, "Audio was not found."));
        if (query.callerRole() == Role.FARMER) {
            if (!owner.equals(query.callerId())) {
                throw new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found.");
            }
        } else if (!staffRegion.allows(query.callerRole(), query.callerId(), query.caseId())) {
            throw new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found.");
        }
        String url = store.presign(locator.objectKey(), presignTtl);
        return new PresignedUrlView(url, clock.instant().plus(presignTtl));
    }
}

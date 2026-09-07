package com.rootcause.foshol.intake.application.query.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.intake.application.IntakeException;
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
    private final Duration presignTtl;
    private final Clock clock;

    public CaseAudioUrlQueryHandler(
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
    @Override
    public PresignedUrlView handle(CaseAudioUrlQuery query) {
        if (queries.findFarmerId(query.caseId()).isEmpty()) {
            throw new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found.");
        }
        CaseQueryPort.AudioLocator locator = queries
                .findAudio(query.caseId())
                .orElseThrow(() -> new IntakeException(ErrorCodes.ERR_AUDIO_NOT_FOUND, 404, "Audio was not found."));
        if (query.callerRole() != Role.OFFICER
                && query.callerRole() != Role.ADMIN
                && !locator.farmerId().equals(query.callerId())) {
            throw new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found.");
        }
        String url = store.presign(locator.objectKey(), presignTtl);
        return new PresignedUrlView(url, clock.instant().plus(presignTtl));
    }
}

package com.rootcause.foshol.analysis.application.query.handler;

import com.rootcause.foshol.analysis.application.AnalysisSettings;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.PresignedUrl;
import com.rootcause.foshol.analysis.application.query.AnalysisReadRepository;
import com.rootcause.foshol.analysis.application.query.GradcamLink;
import com.rootcause.foshol.analysis.application.query.GradcamLinkQuery;
import com.rootcause.foshol.analysis.domain.AnalysisException;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class GradcamLinkQueryHandler implements QueryHandler<GradcamLinkQuery, Optional<GradcamLink>> {

    @Override
    public Class<GradcamLinkQuery> queryType() {
        return GradcamLinkQuery.class;
    }

    private final AnalysisReadRepository reads;
    private final ObjectStorePort objectStore;
    private final CaseIntakeApi intake;
    private final AnalysisSettings settings;

    public GradcamLinkQueryHandler(
            AnalysisReadRepository reads,
            ObjectStorePort objectStore,
            CaseIntakeApi intake,
            AnalysisSettings settings) {
        this.reads = reads;
        this.objectStore = objectStore;
        this.intake = intake;
        this.settings = settings;
    }

    @Override
    public Optional<GradcamLink> handle(GradcamLinkQuery query) {
        if (!visible(query.caseId(), query.callerId(), query.role())) {
            return Optional.empty();
        }
        Optional<String> key = reads.findGradcamObjectKey(query.caseId());
        if (key.isEmpty()) {
            return Optional.empty();
        }
        try {
            PresignedUrl url = objectStore.presign(key.get(), settings.presignTtl());
            UUID imageId = reads.findPrimaryGradcamImageId(query.caseId()).orElse(null);
            return Optional.of(new GradcamLink(query.caseId(), imageId, url.url(), url.expiresAt()));
        } catch (RuntimeException ex) {
            throw new AnalysisException(ErrorCodes.ERR_STORAGE_UNAVAILABLE, 503, "Object store is unavailable.");
        }
    }

    private boolean visible(UUID caseId, UUID callerId, Role role) {
        if (role == Role.OFFICER || role == Role.ADMIN) {
            return true;
        }
        if (role == Role.FARMER && callerId != null) {
            return intake.isOwnedBy(caseId, callerId);
        }
        return false;
    }
}

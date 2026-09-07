package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RejectionView;
import com.rootcause.foshol.review.application.AdvisoryViewMapper;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.CaseRejectionRepository;
import com.rootcause.foshol.review.application.query.CaseAdvisoryQuery;
import com.rootcause.foshol.review.application.query.CaseAdvisoryResult;
import com.rootcause.foshol.review.domain.Advisory;
import com.rootcause.foshol.review.domain.CaseRejection;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseAdvisoryQueryHandler implements QueryHandler<CaseAdvisoryQuery, CaseAdvisoryResult> {

    @Override
    public Class<CaseAdvisoryQuery> queryType() {
        return CaseAdvisoryQuery.class;
    }

    private final AdvisoryRepository advisories;
    private final CaseRejectionRepository rejections;
    private final CaseIntakeApi cases;
    private final KnowledgeQueryApi knowledge;
    private final OfficerLookupApi officers;

    public CaseAdvisoryQueryHandler(
            AdvisoryRepository advisories,
            CaseRejectionRepository rejections,
            CaseIntakeApi cases,
            KnowledgeQueryApi knowledge,
            OfficerLookupApi officers) {
        this.advisories = advisories;
        this.rejections = rejections;
        this.cases = cases;
        this.knowledge = knowledge;
        this.officers = officers;
    }

    @Transactional(readOnly = true)
    @Override
    public CaseAdvisoryResult handle(CaseAdvisoryQuery query) {
        if (query.actor().role() == Role.FARMER && !cases.isOwnedBy(query.caseId(), query.actor().id())) {
            throw new ReviewException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case not found.");
        }
        List<AdvisoryView> history = advisories.findHistoryByCaseId(query.caseId()).stream()
                .map(this::toView)
                .toList();
        AdvisoryView published = advisories.findPublishedByCaseId(query.caseId()).map(this::toView).orElse(null);
        RejectionView rejection = rejections.findByCaseId(query.caseId()).map(this::toRejection).orElse(null);
        if (published == null && rejection == null) {
            throw new ReviewException(ErrorCodes.ERR_ADVISORY_NOT_FOUND, 404, "No advisory for this case.");
        }
        return new CaseAdvisoryResult(published, history, rejection);
    }

    private AdvisoryView toView(Advisory advisory) {
        return AdvisoryViewMapper.toView(advisory, knowledge, officers);
    }

    private RejectionView toRejection(CaseRejection rejection) {
        String officerName = officers.findById(rejection.officerId()).map(o -> o.name()).orElse("");
        return new RejectionView(
                rejection.caseId(),
                rejection.officerId(),
                officerName,
                rejection.reasonCode(),
                rejection.messageBn(),
                rejection.createdAt());
    }
}

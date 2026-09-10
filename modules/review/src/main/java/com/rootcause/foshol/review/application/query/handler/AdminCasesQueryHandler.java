package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.AdminCaseListCriteria;
import com.rootcause.foshol.review.application.query.AdminCasePeriod;
import com.rootcause.foshol.review.application.query.AdminCasesQuery;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import com.rootcause.foshol.review.domain.ReviewException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCasesQueryHandler implements QueryHandler<AdminCasesQuery, OfficerQueuePage> {

    @Override
    public Class<AdminCasesQuery> queryType() {
        return AdminCasesQuery.class;
    }

    private final ReviewQueryPort reads;
    private final OfficerLookupApi officers;
    private final Clock clock;
    private final ZoneId displayZone;

    public AdminCasesQueryHandler(
            ReviewQueryPort reads,
            OfficerLookupApi officers,
            Clock clock,
            @Value("${" + ConfigKeys.I18N_DISPLAY_ZONE + ":Asia/Dhaka}") String displayZone) {
        this.reads = reads;
        this.officers = officers;
        this.clock = clock;
        this.displayZone = ZoneId.of(displayZone);
    }

    @Transactional(readOnly = true)
    @Override
    public OfficerQueuePage handle(AdminCasesQuery query) {
        String district = officers
                .findById(query.callerId())
                .map(o -> o.districtCode())
                .orElseThrow(ReviewException::taskNotFound);
        return reads.findAdminCases(new AdminCaseListCriteria(
                district,
                submittedSince(query.period()),
                query.state(),
                query.kpi(),
                query.officerId(),
                query.cropCode(),
                query.decisionPath(),
                query.resubmission(),
                query.page(),
                query.size()));
    }

    private Instant submittedSince(AdminCasePeriod period) {
        AdminCasePeriod resolved = period == null ? AdminCasePeriod.LIFETIME : period;
        var zoned = clock.instant().atZone(displayZone);
        return switch (resolved) {
            case TODAY -> zoned.toLocalDate().atStartOfDay(displayZone).toInstant();
            case MONTH -> zoned.toLocalDate().withDayOfMonth(1).atStartOfDay(displayZone).toInstant();
            case YEAR -> zoned.toLocalDate().withDayOfYear(1).atStartOfDay(displayZone).toInstant();
            case LIFETIME -> null;
        };
    }
}

package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.common.enums.KpiKind;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.review.application.port.KpiBreachPort;
import com.rootcause.foshol.review.application.query.AdminKpiSummaryQuery;
import com.rootcause.foshol.review.application.query.AdminKpiSummaryView;
import com.rootcause.foshol.review.application.query.AdminKpiSummaryView.OfficerKpiCount;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.common.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminKpiSummaryQueryHandler implements QueryHandler<AdminKpiSummaryQuery, AdminKpiSummaryView> {

    @Override
    public Class<AdminKpiSummaryQuery> queryType() {
        return AdminKpiSummaryQuery.class;
    }

    private final KpiBreachPort breaches;
    private final OfficerLookupApi officers;

    public AdminKpiSummaryQueryHandler(KpiBreachPort breaches, OfficerLookupApi officers) {
        this.breaches = breaches;
        this.officers = officers;
    }

    @Transactional(readOnly = true)
    @Override
    public AdminKpiSummaryView handle(AdminKpiSummaryQuery query) {
        String district = officers
                .findById(query.callerId())
                .map(o -> o.districtCode())
                .orElseThrow(ReviewException::taskNotFound);
        List<OfficerKpiCount> officersCounts = breaches.countResolutionByOfficer(district).stream()
                .map(row -> new OfficerKpiCount(
                        row.officerId(),
                        officers.findById(row.officerId()).map(o -> o.name()).orElse(""),
                        row.resolutionFailures()))
                .toList();
        return new AdminKpiSummaryView(
                breaches.countByKind(district, KpiKind.ASSIGNMENT),
                breaches.countByKind(district, KpiKind.RESOLUTION),
                officersCounts);
    }
}

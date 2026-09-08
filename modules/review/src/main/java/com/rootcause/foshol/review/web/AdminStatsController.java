package com.rootcause.foshol.review.web;

import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.KpiKind;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.review.application.query.AdminCasePeriod;
import com.rootcause.foshol.review.application.query.AdminCasesQuery;
import com.rootcause.foshol.review.application.query.AdminKpiBreachPage;
import com.rootcause.foshol.review.application.query.AdminKpiBreachesQuery;
import com.rootcause.foshol.review.application.query.AdminKpiSummaryQuery;
import com.rootcause.foshol.review.application.query.AdminKpiSummaryView;
import com.rootcause.foshol.review.application.query.AdminStatsQuery;
import com.rootcause.foshol.review.application.query.AdminStatsView;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final QueryBus queries;

    public AdminStatsController(QueryBus queries) {
        this.queries = queries;
    }

    @GetMapping("/stats")
    public AdminStatsView stats(Authentication authentication) {
        return queries.handle(new AdminStatsQuery(ReviewAuth.subjectId(authentication)));
    }

    @GetMapping("/kpis")
    public AdminKpiSummaryView kpis(Authentication authentication) {
        return queries.handle(new AdminKpiSummaryQuery(ReviewAuth.subjectId(authentication)));
    }

    @GetMapping("/kpis/breaches")
    public AdminKpiBreachPage kpiBreaches(
            @RequestParam(name = "kind", required = false) KpiKind kind,
            @RequestParam(name = "officerId", required = false) UUID officerId,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            Authentication authentication) {
        return queries.handle(new AdminKpiBreachesQuery(
                ReviewAuth.subjectId(authentication), kind, officerId, page, size));
    }

    @GetMapping("/cases")
    public OfficerQueuePage cases(
            @RequestParam(name = "period", required = false, defaultValue = "LIFETIME") AdminCasePeriod period,
            @RequestParam(name = "state", required = false, defaultValue = "ALL") String state,
            @RequestParam(name = "kpi", required = false) KpiKind kpi,
            @RequestParam(name = "officerId", required = false) UUID officerId,
            @RequestParam(name = "cropCode", required = false) String cropCode,
            @RequestParam(name = "decisionPath", required = false) DecisionPath decisionPath,
            @RequestParam(name = "resubmission", required = false) Boolean resubmission,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            Authentication authentication) {
        return queries.handle(new AdminCasesQuery(
                ReviewAuth.subjectId(authentication),
                period,
                state,
                kpi,
                officerId,
                cropCode,
                decisionPath,
                resubmission,
                page,
                size));
    }
}

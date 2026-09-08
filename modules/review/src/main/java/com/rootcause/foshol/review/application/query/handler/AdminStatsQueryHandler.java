package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.AdminStatsQuery;
import com.rootcause.foshol.review.application.query.AdminStatsView;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatsQueryHandler implements QueryHandler<AdminStatsQuery, AdminStatsView> {

    @Override
    public Class<AdminStatsQuery> queryType() {
        return AdminStatsQuery.class;
    }

    private final ReviewQueryPort reads;
    private final OfficerLookupApi officers;
    private final Clock clock;
    private final ZoneId displayZone;
    private final BigDecimal confidenceHigh;
    private final BigDecimal confidenceLow;

    public AdminStatsQueryHandler(
            ReviewQueryPort reads,
            OfficerLookupApi officers,
            Clock clock,
            @Value("${" + ConfigKeys.I18N_DISPLAY_ZONE + ":Asia/Dhaka}") String displayZone,
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_HIGH + ":0.75}") BigDecimal confidenceHigh,
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_LOW + ":0.45}") BigDecimal confidenceLow) {
        this.reads = reads;
        this.officers = officers;
        this.clock = clock;
        this.displayZone = ZoneId.of(displayZone);
        this.confidenceHigh = confidenceHigh;
        this.confidenceLow = confidenceLow;
    }

    @Transactional(readOnly = true)
    @Override
    public AdminStatsView handle(AdminStatsQuery query) {
        String district = officers
                .findById(query.callerId())
                .map(o -> o.districtCode())
                .orElseThrow(ReviewException::taskNotFound);
        var zoned = clock.instant().atZone(displayZone);
        Instant dayStart = zoned.toLocalDate().atStartOfDay(displayZone).toInstant();
        Instant monthStart = zoned.toLocalDate().withDayOfMonth(1).atStartOfDay(displayZone).toInstant();
        Instant yearStart = zoned.toLocalDate().withDayOfYear(1).atStartOfDay(displayZone).toInstant();
        return reads.loadStats(dayStart, monthStart, yearStart, confidenceHigh, confidenceLow, district);
    }
}

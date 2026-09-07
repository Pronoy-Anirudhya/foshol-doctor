package com.rootcause.foshol.review.application.query.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.AdminStatsQuery;
import com.rootcause.foshol.review.application.query.AdminStatsView;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
    private final Clock clock;
    private final ZoneId displayZone;
    private final BigDecimal confidenceHigh;
    private final BigDecimal confidenceLow;

    public AdminStatsQueryHandler(
            ReviewQueryPort reads,
            Clock clock,
            @Value("${" + ConfigKeys.I18N_DISPLAY_ZONE + ":Asia/Dhaka}") String displayZone,
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_HIGH + ":0.75}") BigDecimal confidenceHigh,
            @Value("${" + ConfigKeys.ANALYSIS_CONFIDENCE_LOW + ":0.45}") BigDecimal confidenceLow) {
        this.reads = reads;
        this.clock = clock;
        this.displayZone = ZoneId.of(displayZone);
        this.confidenceHigh = confidenceHigh;
        this.confidenceLow = confidenceLow;
    }

    @Transactional(readOnly = true)
    @Override
    public AdminStatsView handle(AdminStatsQuery query) {
        var start = LocalDate.now(clock.withZone(displayZone)).atStartOfDay(displayZone).toInstant();
        return reads.loadStats(start, confidenceHigh, confidenceLow);
    }
}

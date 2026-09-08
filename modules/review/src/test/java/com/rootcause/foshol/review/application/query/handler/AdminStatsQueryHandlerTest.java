package com.rootcause.foshol.review.application.query.handler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.AdminStatsQuery;
import com.rootcause.foshol.review.application.query.AdminStatsView;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class AdminStatsQueryHandlerTest {

    @Mock
    private ReviewQueryPort reads;

    @Mock
    private com.rootcause.foshol.identity.api.OfficerLookupApi officers;

    @Test
    void returnsThresholdsFromProperties() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        java.util.UUID adminId = java.util.UUID.fromString("01800000-0000-7000-8000-000000000301");
        when(officers.findById(adminId))
                .thenReturn(java.util.Optional.of(new com.rootcause.foshol.identity.api.OfficerView(
                        adminId, "Admin", "DHA", "ADMIN", true, "DHK")));
        AdminStatsView view = new AdminStatsView(0, null, null, null, 0, new BigDecimal("0.75"), new BigDecimal("0.45"));
        when(reads.loadStats(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("DHA")))
                .thenReturn(view);
        AdminStatsQueryHandler handler = new AdminStatsQueryHandler(
                reads,
                officers,
                Clock.fixed(t0, ZoneOffset.UTC),
                "Asia/Dhaka",
                new BigDecimal("0.75"),
                new BigDecimal("0.45"));
        AdminStatsView result = handler.handle(new AdminStatsQuery(java.util.UUID.fromString("01800000-0000-7000-8000-000000000301")));
        assertThat(result.confidenceHigh()).isEqualByComparingTo("0.75");
        assertThat(result.confidenceLow()).isEqualByComparingTo("0.45");
        assertThat(result.approvalRate()).isNull();
    }
}

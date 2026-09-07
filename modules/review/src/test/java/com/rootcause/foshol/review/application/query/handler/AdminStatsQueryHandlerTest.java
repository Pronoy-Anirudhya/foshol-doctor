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

    @Test
    void returnsThresholdsFromProperties() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        AdminStatsView view = new AdminStatsView(0, null, null, null, 0, new BigDecimal("0.75"), new BigDecimal("0.45"));
        when(reads.loadStats(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(view);
        AdminStatsQueryHandler handler = new AdminStatsQueryHandler(
                reads,
                Clock.fixed(t0, ZoneOffset.UTC),
                "Asia/Dhaka",
                new BigDecimal("0.75"),
                new BigDecimal("0.45"));
        AdminStatsView result = handler.handle(new AdminStatsQuery());
        assertThat(result.confidenceHigh()).isEqualByComparingTo("0.75");
        assertThat(result.confidenceLow()).isEqualByComparingTo("0.45");
        assertThat(result.approvalRate()).isNull();
    }
}

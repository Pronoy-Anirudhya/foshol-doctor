package com.rootcause.foshol.review.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.AdminStatsQuery;
import com.rootcause.foshol.review.application.query.AdminStatsView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminStatsQueryHandlerTest {

    @Mock
    private ReviewQueryPort reads;

    @Mock
    private OfficerLookupApi officers;

    @Test
    void returnsThresholdsFromProperties() {
        Instant t0 = Instant.parse("2026-01-15T12:00:00Z");
        UUID adminId = UUID.fromString("01800000-0000-7000-8000-000000000301");
        when(officers.findById(adminId))
                .thenReturn(Optional.of(new OfficerView(adminId, "Admin", "DHA", "ADMIN", true, "DHK")));
        AdminStatsView view = new AdminStatsView(
                0, 0, 0, 0, null, null, null, 0, null, new BigDecimal("0.75"), new BigDecimal("0.45"));
        when(reads.loadStats(any(), any(), any(), any(), any(), eq("DHA"))).thenReturn(view);
        AdminStatsQueryHandler handler = new AdminStatsQueryHandler(
                reads,
                officers,
                Clock.fixed(t0, ZoneOffset.UTC),
                "Asia/Dhaka",
                new BigDecimal("0.75"),
                new BigDecimal("0.45"));
        AdminStatsView result = handler.handle(new AdminStatsQuery(adminId));
        assertThat(result.confidenceHigh()).isEqualByComparingTo("0.75");
        assertThat(result.confidenceLow()).isEqualByComparingTo("0.45");
        assertThat(result.approvalRate()).isNull();
        assertThat(result.rejectionRate()).isNull();
        ArgumentCaptor<Instant> day = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> month = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> year = ArgumentCaptor.forClass(Instant.class);
        verify(reads)
                .loadStats(
                        day.capture(),
                        month.capture(),
                        year.capture(),
                        eq(new BigDecimal("0.75")),
                        eq(new BigDecimal("0.45")),
                        eq("DHA"));
        assertThat(day.getValue()).isEqualTo(Instant.parse("2026-01-14T18:00:00Z"));
        assertThat(month.getValue()).isEqualTo(Instant.parse("2025-12-31T18:00:00Z"));
        assertThat(year.getValue()).isEqualTo(Instant.parse("2025-12-31T18:00:00Z"));
    }
}

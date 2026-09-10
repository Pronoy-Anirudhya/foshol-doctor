package com.rootcause.foshol.review.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.DecisionPath;
import com.rootcause.foshol.common.enums.KpiKind;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.AdminCaseListCriteria;
import com.rootcause.foshol.review.application.query.AdminCasePeriod;
import com.rootcause.foshol.review.application.query.AdminCasesQuery;
import com.rootcause.foshol.review.application.query.OfficerQueuePage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminCasesQueryHandlerTest {

    @Mock
    private ReviewQueryPort reads;

    @Mock
    private OfficerLookupApi officers;

    @Test
    void scopesToCallerDistrictAndMapsCuratedFilters() {
        Instant t0 = Instant.parse("2026-03-10T00:00:00Z");
        UUID adminId = UUID.fromString("01800000-0000-7000-8000-000000000301");
        UUID officerId = UUID.fromString("01800000-0000-7000-8000-000000000201");
        when(officers.findById(adminId))
                .thenReturn(Optional.of(new OfficerView(adminId, "Admin", "DHA", "ADMIN", true, "DHK")));
        when(reads.findAdminCases(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new OfficerQueuePage(List.of(), 0, 20, 0, 0));
        AdminCasesQueryHandler handler =
                new AdminCasesQueryHandler(reads, officers, Clock.fixed(t0, ZoneOffset.UTC), "Asia/Dhaka");
        handler.handle(new AdminCasesQuery(
                adminId,
                AdminCasePeriod.MONTH,
                "PENDING",
                KpiKind.ASSIGNMENT,
                officerId,
                "RICE",
                DecisionPath.PRIMARY,
                true,
                1,
                10));
        ArgumentCaptor<AdminCaseListCriteria> captor = ArgumentCaptor.forClass(AdminCaseListCriteria.class);
        verify(reads).findAdminCases(captor.capture());
        AdminCaseListCriteria criteria = captor.getValue();
        assertThat(criteria.districtCode()).isEqualTo("DHA");
        assertThat(criteria.submittedSince()).isEqualTo(Instant.parse("2026-02-28T18:00:00Z"));
        assertThat(criteria.state()).isEqualTo("PENDING");
        assertThat(criteria.kpi()).isEqualTo(KpiKind.ASSIGNMENT);
        assertThat(criteria.officerId()).isEqualTo(officerId);
        assertThat(criteria.cropCode()).isEqualTo("RICE");
        assertThat(criteria.decisionPath()).isEqualTo(DecisionPath.PRIMARY);
        assertThat(criteria.resubmission()).isTrue();
        assertThat(criteria.page()).isEqualTo(1);
        assertThat(criteria.size()).isEqualTo(10);
    }

    @Test
    void lifetimeHasNoSubmittedSinceBound() {
        Instant t0 = Instant.parse("2026-03-10T00:00:00Z");
        UUID adminId = UUID.fromString("01800000-0000-7000-8000-000000000301");
        when(officers.findById(adminId))
                .thenReturn(Optional.of(new OfficerView(adminId, "Admin", "DHA", "ADMIN", true, "DHK")));
        when(reads.findAdminCases(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new OfficerQueuePage(List.of(), 0, 20, 0, 0));
        AdminCasesQueryHandler handler =
                new AdminCasesQueryHandler(reads, officers, Clock.fixed(t0, ZoneOffset.UTC), "Asia/Dhaka");
        handler.handle(new AdminCasesQuery(
                adminId, AdminCasePeriod.LIFETIME, "ALL", null, null, null, null, null, 0, 20));
        ArgumentCaptor<AdminCaseListCriteria> captor = ArgumentCaptor.forClass(AdminCaseListCriteria.class);
        verify(reads).findAdminCases(captor.capture());
        assertThat(captor.getValue().submittedSince()).isNull();
    }
}

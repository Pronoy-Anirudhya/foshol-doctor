package com.rootcause.foshol.intake.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.intake.domain.IntakeException;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.port.ImageStorePort;
import com.rootcause.foshol.intake.application.query.CaseDetailQuery;
import com.rootcause.foshol.intake.application.query.CaseDetailView;
import com.rootcause.foshol.intake.application.query.CaseImageUrlQuery;
import com.rootcause.foshol.intake.application.query.FarmerCaseListQuery;
import com.rootcause.foshol.intake.application.query.FarmerCaseRow;
import com.rootcause.foshol.intake.application.query.PageResult;
import com.rootcause.foshol.intake.application.query.PresignedUrlView;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class CaseQueryHandlerTest {

    private static final UUID CASE = UUID.fromString("018f0000-0000-7000-8000-000000000010");
    private static final UUID IMAGE = UUID.fromString("018f0000-0000-7000-8000-000000000011");
    private static final UUID OWNER = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("018f0000-0000-7000-8000-000000000002");

    @Mock
    private CaseQueryPort queries;

    @Mock
    private ImageStorePort store;

    @Mock
    private com.rootcause.foshol.intake.application.query.StaffRegionAccess staffRegion;

    @Test
    void farmerCannotSeeAnotherCase() {
        when(queries.findFarmerId(CASE)).thenReturn(Optional.of(OWNER));
        CaseDetailQueryHandler handler = new CaseDetailQueryHandler(queries, staffRegion);
        assertThatThrownBy(() -> handler.handle(new CaseDetailQuery(CASE, OTHER, Role.FARMER)))
                .isInstanceOf(IntakeException.class)
                .extracting(ex -> ((IntakeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CASE_NOT_FOUND);
    }

    @Test
    void officerMayReadAnyCase() {
        when(queries.findFarmerId(CASE)).thenReturn(Optional.of(OWNER));
        when(queries.findDetail(CASE)).thenReturn(Optional.of(new CaseDetailView(
                CASE, UUID.randomUUID(), "ধান", "Rice", false, null, null, null, null, java.util.List.of(), null, Instant.now(),
                java.math.BigDecimal.ONE, com.rootcause.foshol.common.enums.FieldAreaUnit.DECIMAL, null, null,
                com.rootcause.foshol.common.enums.MetricsSource.FORM)));
        when(staffRegion.allows(Role.OFFICER, OTHER, CASE)).thenReturn(true);
        CaseDetailQueryHandler handler = new CaseDetailQueryHandler(queries, staffRegion);
        assertThat(handler.handle(new CaseDetailQuery(CASE, OTHER, Role.OFFICER)).caseId()).isEqualTo(CASE);
    }

    @Test
    void imageUrlUsesPresign() {
        when(queries.findFarmerId(CASE)).thenReturn(Optional.of(OWNER));
        when(queries.findImage(CASE, IMAGE))
                .thenReturn(Optional.of(new CaseQueryPort.ImageLocator(CASE, OWNER, "orig", "deriv")));
        when(store.presign("orig", Duration.ofMinutes(10))).thenReturn("https://example.test/orig");
        CaseImageUrlQueryHandler handler =
                new CaseImageUrlQueryHandler(queries, store, staffRegion, Clock.fixed(Instant.now(), ZoneOffset.UTC), Duration.ofMinutes(10));
        PresignedUrlView view =
                handler.handle(new CaseImageUrlQuery(CASE, IMAGE, OWNER, Role.FARMER, false));
        assertThat(view.url()).contains("orig");
    }

    @Test
    void listClampsSize() {
        when(queries.listHistory(OWNER, 0, 100)).thenReturn(PageResult.of(java.util.List.of(), 0, 100, 0));
        FarmerCaseListQueryHandler handler = new FarmerCaseListQueryHandler(queries);
        PageResult<FarmerCaseRow> page = handler.handle(new FarmerCaseListQuery(OWNER, 0, 500));
        assertThat(page.size()).isEqualTo(100);
    }
}

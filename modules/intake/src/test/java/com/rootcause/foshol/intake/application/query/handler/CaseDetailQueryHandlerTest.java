package com.rootcause.foshol.intake.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.CaseStatus;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.intake.domain.IntakeException;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.query.CaseDetailQuery;
import com.rootcause.foshol.intake.application.query.CaseDetailView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class CaseDetailQueryHandlerTest {

    @Mock
    private CaseQueryPort queries;

    @Mock
    private com.rootcause.foshol.intake.application.query.StaffRegionAccess staffRegion;

    @Test
    void hidesAnotherFarmersCase() {
        UUID caseId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        when(queries.findFarmerId(caseId)).thenReturn(Optional.of(owner));
        CaseDetailQueryHandler handler = new CaseDetailQueryHandler(queries, staffRegion);
        assertThatThrownBy(() -> handler.handle(new CaseDetailQuery(caseId, UUID.randomUUID(), Role.FARMER)))
                .isInstanceOf(IntakeException.class)
                .extracting(ex -> ((IntakeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CASE_NOT_FOUND);
    }

    @Test
    void officerCanReadAnyCase() {
        UUID caseId = UUID.randomUUID();
        CaseDetailView view = new CaseDetailView(
                caseId,
                UUID.randomUUID(),
                "ধান",
                CaseStatus.SUBMITTED,
                null,
                null,
                null,
                List.of(),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                new java.math.BigDecimal("1"),
                com.rootcause.foshol.common.enums.FieldAreaUnit.DECIMAL,
                null,
                null,
                com.rootcause.foshol.common.enums.MetricsSource.FORM);
        when(queries.findFarmerId(caseId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(queries.findDetail(caseId)).thenReturn(Optional.of(view));
        when(staffRegion.allows(org.mockito.ArgumentMatchers.eq(Role.OFFICER), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(caseId)))
                .thenReturn(true);
        CaseDetailQueryHandler handler = new CaseDetailQueryHandler(queries, staffRegion);
        assertThat(handler.handle(new CaseDetailQuery(caseId, UUID.randomUUID(), Role.OFFICER))).isEqualTo(view);
    }
}

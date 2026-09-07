package com.rootcause.foshol.intake.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.CaseStatus;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseDetailQueryHandlerTest {

    @Mock
    private CaseQueryPort queries;

    @Test
    void hidesAnotherFarmersCase() {
        UUID caseId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        when(queries.findFarmerId(caseId)).thenReturn(Optional.of(owner));
        CaseDetailQueryHandler handler = new CaseDetailQueryHandler(queries);
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
                Instant.parse("2026-01-01T00:00:00Z"));
        when(queries.findFarmerId(caseId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(queries.findDetail(caseId)).thenReturn(Optional.of(view));
        CaseDetailQueryHandler handler = new CaseDetailQueryHandler(queries);
        assertThat(handler.handle(new CaseDetailQuery(caseId, UUID.randomUUID(), Role.OFFICER))).isEqualTo(view);
    }
}

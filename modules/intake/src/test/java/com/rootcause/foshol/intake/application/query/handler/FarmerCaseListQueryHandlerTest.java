package com.rootcause.foshol.intake.application.query.handler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.intake.application.port.CaseQueryPort;
import com.rootcause.foshol.intake.application.query.FarmerCaseListQuery;
import com.rootcause.foshol.intake.application.query.FarmerCaseRow;
import com.rootcause.foshol.intake.application.query.PageResult;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class FarmerCaseListQueryHandlerTest {

    @Mock
    private CaseQueryPort queries;

    @Test
    void clampsPageSize() {
        UUID farmer = UUID.randomUUID();
        when(queries.listHistory(farmer, 0, 100)).thenReturn(PageResult.of(List.of(), 0, 100, 0));
        FarmerCaseListQueryHandler handler = new FarmerCaseListQueryHandler(queries);
        PageResult<FarmerCaseRow> result = handler.handle(new FarmerCaseListQuery(farmer, -1, 500));
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.page()).isEqualTo(0);
    }
}

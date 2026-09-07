package com.rootcause.foshol.analysis.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.api.AnalysisView;
import com.rootcause.foshol.common.AiMode;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisDetailQueryHandlerTest {

    private static final UUID CASE_ID = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");
    private static final UUID FARMER_A = UUID.fromString("018f0000-0000-7000-8000-0000000000a1");
    private static final UUID FARMER_B = UUID.fromString("018f0000-0000-7000-8000-0000000000a2");

    @Mock
    private AnalysisReadRepository reads;

    @Mock
    private CaseIntakeApi intake;

    @Test
    void farmerOfAnotherCaseGetsEmpty() {
        when(intake.isOwnedBy(CASE_ID, FARMER_B)).thenReturn(false);
        AnalysisDetailQueryHandler handler = new AnalysisDetailQueryHandler(reads, intake);
        assertThat(handler.handle(new AnalysisDetailQuery(CASE_ID, FARMER_B, Role.FARMER))).isEmpty();
    }

    @Test
    void officerMayReadAnyCase() {
        AnalysisView view = new AnalysisView(
                CASE_ID,
                DecisionPath.PRIMARY,
                AiMode.REPLAY,
                new BigDecimal("0.82"),
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                List.of(),
                "m",
                "v1",
                12,
                null);
        when(reads.findByCaseId(CASE_ID)).thenReturn(Optional.of(view));
        AnalysisDetailQueryHandler handler = new AnalysisDetailQueryHandler(reads, intake);
        assertThat(handler.handle(new AnalysisDetailQuery(CASE_ID, FARMER_A, Role.OFFICER)))
                .contains(view);
    }
}

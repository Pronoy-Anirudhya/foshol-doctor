package com.rootcause.foshol.review.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.RejectionReason;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.review.application.command.Actor;
import com.rootcause.foshol.review.application.port.AdvisoryRepository;
import com.rootcause.foshol.review.application.port.CaseRejectionRepository;
import com.rootcause.foshol.review.application.query.CaseAdvisoryQuery;
import com.rootcause.foshol.review.application.query.CaseAdvisoryResult;
import com.rootcause.foshol.review.domain.CaseRejection;
import com.rootcause.foshol.review.domain.ReviewException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class CaseAdvisoryQueryHandlerTest {

    @Mock
    private AdvisoryRepository advisories;

    @Mock
    private CaseRejectionRepository rejections;

    @Mock
    private CaseIntakeApi cases;

    @Mock
    private KnowledgeQueryApi knowledge;

    @Mock
    private OfficerLookupApi officers;

    @Test
    void farmerOfAnotherCaseGets404() {
        UUID caseId = Uuid7.create();
        UUID farmer = Uuid7.create();
        when(cases.isOwnedBy(caseId, farmer)).thenReturn(false);
        CaseAdvisoryQueryHandler handler =
                new CaseAdvisoryQueryHandler(advisories, rejections, cases, knowledge, officers);
        assertThatThrownBy(() -> handler.handle(new CaseAdvisoryQuery(caseId, new Actor(farmer, Role.FARMER))))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CASE_NOT_FOUND);
        assertThatThrownBy(() -> handler.handle(new CaseAdvisoryQuery(caseId, new Actor(farmer, Role.FARMER))))
                .extracting(ex -> ((ReviewException) ex).status())
                .isEqualTo(404);
    }

    @Test
    void rejectionOnlyIsReturned() {
        UUID caseId = Uuid7.create();
        UUID officer = Uuid7.create();
        when(rejections.findByCaseId(caseId))
                .thenReturn(Optional.of(new CaseRejection(
                        Uuid7.create(),
                        caseId,
                        officer,
                        RejectionReason.BLURRY_IMAGE,
                        "ছবি",
                        Instant.parse("2026-01-01T00:00:00Z"))));
        when(advisories.findHistoryByCaseId(caseId)).thenReturn(List.of());
        when(advisories.findPublishedByCaseId(caseId)).thenReturn(Optional.empty());
        when(officers.findById(officer))
                .thenReturn(Optional.of(new OfficerView(officer, "Officer A", "DHK01", "OFFICER", true, "DHK")));
        when(cases.findById(caseId)).thenReturn(Optional.of(new com.rootcause.foshol.intake.api.CaseSummary(
                caseId,
                Uuid7.create(),
                Uuid7.create(),
                "rice",
                "DHK01",
                "DHK",
                com.rootcause.foshol.common.enums.CaseStatus.REJECTED,
                null,
                null,
                null,
                List.of(),
                null,
                "c",
                Instant.parse("2026-01-01T00:00:00Z"),
                java.math.BigDecimal.ONE,
                com.rootcause.foshol.common.enums.FieldAreaUnit.DECIMAL,
                null,
                null,
                com.rootcause.foshol.common.enums.MetricsSource.FORM)));
        CaseAdvisoryQueryHandler handler =
                new CaseAdvisoryQueryHandler(advisories, rejections, cases, knowledge, officers);
        CaseAdvisoryResult result =
                handler.handle(new CaseAdvisoryQuery(caseId, new Actor(officer, Role.OFFICER)));
        assertThat(result.published()).isNull();
        assertThat(result.rejection().messageBn()).isEqualTo("ছবি");
    }
}

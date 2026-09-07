package com.rootcause.foshol.knowledge.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.RemedyType;
import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListActiveRemediesQueryHandlerTest {

    @Test
    void returnsActiveRemediesInPortOrder() {
        KnowledgeReadPort reads = mock(KnowledgeReadPort.class);
        UUID disease = UUID.fromString("01800000-0000-7000-8000-000000000103");
        UUID first = UUID.fromString("01800000-0000-7000-8000-000000000701");
        UUID second = UUID.fromString("01800000-0000-7000-8000-000000000702");
        RemedyReadModel a = new RemedyReadModel(
                first, disease, RemedyType.CULTURAL, "a", List.of("a"), null, null, "LOW", "LOW", "src", 1);
        RemedyReadModel b = new RemedyReadModel(
                second, disease, RemedyType.ORGANIC, "b", List.of("b"), null, null, "LOW", "LOW", "src", 2);
        when(reads.listActiveRemedies(disease)).thenReturn(List.of(a, b));
        ListActiveRemediesQueryHandler handler = new ListActiveRemediesQueryHandler(reads);
        assertThat(handler.handle(new ListActiveRemediesQuery(disease))).containsExactly(a, b);
    }
}

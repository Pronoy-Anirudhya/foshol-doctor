package com.rootcause.foshol.knowledge.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.enums.Severity;
import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.DiseaseReadModel;
import com.rootcause.foshol.knowledge.application.query.ListDiseasesByCropQuery;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ListDiseasesByCropQueryHandlerTest {

    @Test
    void healthyDiseaseIsLastWhenPortOrdersThatWay() {
        KnowledgeReadPort reads = mock(KnowledgeReadPort.class);
        UUID crop = UUID.fromString("01800000-0000-7000-8000-000000000001");
        UUID blast = UUID.fromString("01800000-0000-7000-8000-000000000103");
        UUID healthy = UUID.fromString("01800000-0000-7000-8000-000000000106");
        DiseaseReadModel sick =
                new DiseaseReadModel(blast, crop, "blast", "Blast", "Blast", null, null, Severity.LOW, false);
        DiseaseReadModel well =
                new DiseaseReadModel(healthy, crop, "healthy", "Healthy", "Healthy", null, null, Severity.NONE, true);
        when(reads.listDiseasesByCrop(crop)).thenReturn(List.of(sick, well));
        ListDiseasesByCropQueryHandler handler = new ListDiseasesByCropQueryHandler(reads);
        assertThat(handler.handle(new ListDiseasesByCropQuery(crop)))
                .extracting(DiseaseReadModel::healthy)
                .containsExactly(false, true);
    }
}

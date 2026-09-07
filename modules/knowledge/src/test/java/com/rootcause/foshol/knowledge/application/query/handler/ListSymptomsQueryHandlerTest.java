package com.rootcause.foshol.knowledge.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.ListSymptomsQuery;
import com.rootcause.foshol.knowledge.application.query.SymptomReadModel;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ListSymptomsQueryHandlerTest {

    @Test
    void returnsSymptomsOrderedByCodeFromPort() {
        KnowledgeReadPort reads = mock(KnowledgeReadPort.class);
        UUID id = UUID.fromString("01800000-0000-7000-8000-000000000501");
        SymptomReadModel row = new SymptomReadModel(id, "leaf_yellow", "হলুদ", "Yellow", "LEAF");
        when(reads.listSymptoms()).thenReturn(List.of(row));
        ListSymptomsQueryHandler handler = new ListSymptomsQueryHandler(reads);
        assertThat(handler.handle(new ListSymptomsQuery())).containsExactly(row);
    }
}

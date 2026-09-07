package com.rootcause.foshol.knowledge.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import org.junit.jupiter.api.Test;

class FindDiseaseByIdQueryHandlerTest {

    @Test
    void nullIdIsEmpty() {
        KnowledgeReadPort reads = mock(KnowledgeReadPort.class);
        FindDiseaseByIdQueryHandler handler = new FindDiseaseByIdQueryHandler(reads);
        assertThat(handler.handle(new FindDiseaseByIdQuery(null))).isEmpty();
        verifyNoInteractions(reads);
    }
}

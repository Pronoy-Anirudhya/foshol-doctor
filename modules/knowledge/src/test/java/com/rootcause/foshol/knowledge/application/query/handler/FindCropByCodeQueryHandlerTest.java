package com.rootcause.foshol.knowledge.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.FindCropByCodeQuery;

import org.junit.jupiter.api.Test;

class FindCropByCodeQueryHandlerTest {

    @Test
    void nullCodeIsEmpty() {
        KnowledgeReadPort reads = mock(KnowledgeReadPort.class);
        FindCropByCodeQueryHandler handler = new FindCropByCodeQueryHandler(reads);
        assertThat(handler.handle(new FindCropByCodeQuery(null))).isEmpty();
        verifyNoInteractions(reads);
    }
}

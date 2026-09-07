package com.rootcause.foshol.knowledge.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FindCropByIdQueryHandlerTest {

    @Test
    void unknownIdIsEmpty() {
        KnowledgeReadPort reads = mock(KnowledgeReadPort.class);
        UUID id = UUID.fromString("01800000-0000-7000-8000-000000000099");
        when(reads.findCropById(id)).thenReturn(Optional.empty());
        FindCropByIdQueryHandler handler = new FindCropByIdQueryHandler(reads);
        assertThat(handler.handle(new FindCropByIdQuery(id))).isEmpty();
    }

    @Test
    void nullIdIsEmptyWithoutCallingPort() {
        KnowledgeReadPort reads = mock(KnowledgeReadPort.class);
        FindCropByIdQueryHandler handler = new FindCropByIdQueryHandler(reads);
        assertThat(handler.handle(new FindCropByIdQuery(null))).isEmpty();
        verifyNoInteractions(reads);
    }
}

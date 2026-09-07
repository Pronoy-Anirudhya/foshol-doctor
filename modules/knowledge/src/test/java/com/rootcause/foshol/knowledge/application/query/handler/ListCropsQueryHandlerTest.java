package com.rootcause.foshol.knowledge.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.knowledge.application.port.KnowledgeReadPort;
import com.rootcause.foshol.knowledge.application.query.CropReadModel;
import com.rootcause.foshol.knowledge.application.query.ListCropsQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ListCropsQueryHandlerTest {

    @Test
    void returnsCropsInPortOrder() {
        KnowledgeReadPort reads = mock(KnowledgeReadPort.class);
        UUID rice = UUID.fromString("01800000-0000-7000-8000-000000000001");
        CropReadModel crop = new CropReadModel(rice, "rice", "ধান", "Rice", "crop-rice", 1);
        when(reads.listCrops()).thenReturn(List.of(crop));
        ListCropsQueryHandler handler = new ListCropsQueryHandler(reads);
        assertThat(handler.handle(new ListCropsQuery())).containsExactly(crop);
    }
}

package com.rootcause.foshol.knowledge.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.knowledge.application.port.ModelLabelIndex;
import com.rootcause.foshol.knowledge.application.query.ResolveModelLabelQuery;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ResolveModelLabelQueryHandlerTest {

    @Test
    void caseSensitiveMissIsEmpty() {
        ModelLabelIndex labels = mock(ModelLabelIndex.class);
        when(labels.resolve("m", "1", "leaf_blast")).thenReturn(Optional.empty());
        ResolveModelLabelQueryHandler handler = new ResolveModelLabelQueryHandler(labels);
        assertThat(handler.handle(new ResolveModelLabelQuery("m", "1", "leaf_blast"))).isEmpty();
    }

    @Test
    void hitReturnsDiseaseId() {
        ModelLabelIndex labels = mock(ModelLabelIndex.class);
        UUID disease = UUID.fromString("01800000-0000-7000-8000-000000000103");
        when(labels.resolve("m", "1", "Leaf_Blast")).thenReturn(Optional.of(disease));
        ResolveModelLabelQueryHandler handler = new ResolveModelLabelQueryHandler(labels);
        assertThat(handler.handle(new ResolveModelLabelQuery("m", "1", "Leaf_Blast"))).contains(disease);
    }
}

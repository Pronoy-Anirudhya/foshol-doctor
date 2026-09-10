package com.rootcause.foshol.knowledge.application.query.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.knowledge.api.SymptomMatchRequest;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import com.rootcause.foshol.knowledge.application.config.MatchSettings;
import com.rootcause.foshol.knowledge.application.port.CropIdIndex;
import com.rootcause.foshol.knowledge.application.port.DiseaseScoringPort;
import com.rootcause.foshol.knowledge.application.port.PhraseIndex;
import com.rootcause.foshol.knowledge.application.port.SymptomCatalog;
import com.rootcause.foshol.knowledge.application.port.SymptomPhraseVectorPort;
import com.rootcause.foshol.knowledge.application.query.SymptomMatchQuery;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import com.rootcause.foshol.knowledge.domain.KnowledgeTaxonomy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SymptomMatchQueryHandlerTest {

    private static final UUID RICE = UUID.fromString("01800000-0000-7000-8000-000000000001");

    private CropIdIndex cropIds;
    private PhraseIndex phrases;
    private SymptomCatalog symptoms;
    private SymptomPhraseVectorPort vectors;
    private DiseaseScoringPort scoring;
    private SymptomMatchQueryHandler handler;

    @BeforeEach
    void setUp() {
        cropIds = mock(CropIdIndex.class);
        phrases = mock(PhraseIndex.class);
        symptoms = mock(SymptomCatalog.class);
        vectors = mock(SymptomPhraseVectorPort.class);
        scoring = mock(DiseaseScoringPort.class);
        MatchSettings settings = new MatchSettings(
                new BigDecimal("0.72"),
                new BigDecimal("0.60"),
                8,
                25,
                new BigDecimal("0.30"));
        handler = new SymptomMatchQueryHandler(cropIds, phrases, symptoms, vectors, scoring, settings);
        when(phrases.entries()).thenReturn(List.of());
    }

    @Test
    void emptyKnowledgeBaseIsInconclusive() {
        when(cropIds.existsLive(RICE)).thenReturn(true);
        SymptomMatchResult result = handler.handle(new SymptomMatchQuery(
                new SymptomMatchRequest(RICE, "পাতা হলুদ", null, List.of())));
        assertThat(result.inconclusive()).isTrue();
        assertThat(result.symptoms()).isEmpty();
        assertThat(result.diseases()).isEmpty();
        verifyNoInteractions(vectors);
        verifyNoInteractions(scoring);
    }

    @Test
    void unknownCropRaisesBeforePhraseQuery() {
        when(cropIds.existsLive(RICE)).thenReturn(false);
        assertThatThrownBy(() -> handler.handle(new SymptomMatchQuery(
                        new SymptomMatchRequest(RICE, "পাতা", new float[KnowledgeTaxonomy.EMBEDDING_DIMENSIONS], List.of()))))
                .isInstanceOf(KnowledgeException.class)
                .extracting(ex -> ((KnowledgeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CROP_NOT_FOUND);
        verifyNoInteractions(vectors);
        verify(phrases, never()).entries();
    }

    @Test
    void wrongEmbeddingDimensionIsRejected() {
        when(cropIds.existsLive(RICE)).thenReturn(true);
        assertThatThrownBy(() -> handler.handle(new SymptomMatchQuery(
                        new SymptomMatchRequest(RICE, "পাতা", new float[512], List.of()))))
                .isInstanceOf(KnowledgeException.class)
                .extracting(ex -> ((KnowledgeException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_EMBEDDING_DIMENSION);
        verifyNoInteractions(vectors);
    }
}

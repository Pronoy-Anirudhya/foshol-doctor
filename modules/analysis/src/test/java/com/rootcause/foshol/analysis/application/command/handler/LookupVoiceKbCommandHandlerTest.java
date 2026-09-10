package com.rootcause.foshol.analysis.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.application.command.LookupVoiceKbCommand;
import com.rootcause.foshol.analysis.application.command.VoiceKbLookupResult;
import com.rootcause.foshol.analysis.application.port.EmbeddingResult;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TextEmbeddingPort;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.domain.AnalysisException;
import com.rootcause.foshol.analysis.domain.VoiceKbMatchers;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.api.CropView;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.MatchedSymptom;
import com.rootcause.foshol.knowledge.api.ScoredDisease;
import com.rootcause.foshol.knowledge.api.SymptomMatchApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import com.rootcause.foshol.common.Severity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LookupVoiceKbCommandHandlerTest {

    static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");
    static final UUID BLAST = UUID.fromString("01800000-0000-7000-8000-000000000103");
    static final UUID BROWN = UUID.fromString("01800000-0000-7000-8000-000000000101");

    @Mock
    KnowledgeQueryApi knowledge;

    @Mock
    SymptomMatchApi symptomMatch;

    @Mock
    SpeechToTextPort speech;

    @Mock
    TextEmbeddingPort embedding;

    LookupVoiceKbCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LookupVoiceKbCommandHandler(knowledge, symptomMatch, speech, embedding);
    }

    @Test
    void rejectsUnknownCrop() {
        when(knowledge.findCropById(CROP)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> handler.handle(command("bn")))
                .isInstanceOf(AnalysisException.class)
                .extracting(ex -> ((AnalysisException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_CROP_NOT_FOUND);
        verify(speech, never()).transcribeAudio(any(), any(), any());
    }

    @Test
    void rejectsEmptyAudio() {
        when(knowledge.findCropById(CROP)).thenReturn(Optional.of(crop()));
        LookupVoiceKbCommand empty = new LookupVoiceKbCommand(CROP, new byte[0], "bn", "corr");
        assertThatThrownBy(() -> handler.handle(empty))
                .isInstanceOf(AnalysisException.class)
                .extracting(ex -> ((AnalysisException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_AUDIO_UNREADABLE);
    }

    @Test
    void silentTranscriptIsInconclusive() {
        stubCrop();
        when(speech.transcribeAudio(any(), eq(VoiceKbMatchers.LANGUAGE_BN), eq("corr")))
                .thenReturn(new TranscriptResult("asr", "  ", BigDecimal.ZERO, 10));
        VoiceKbLookupResult result = handler.handle(command(null));
        assertThat(result.inconclusive()).isTrue();
        assertThat(result.candidates()).isEmpty();
        verify(embedding, never()).embed(any());
        verify(knowledge, never()).listActiveRemedies(any());
    }

    @Test
    void nameHitReturnsCatalogueCandidateWithoutRemedies() {
        stubCrop();
        stubCatalogue();
        when(speech.transcribeAudio(any(), eq("bn"), eq("corr")))
                .thenReturn(new TranscriptResult("asr", "blast", new BigDecimal("0.910"), 12));
        when(embedding.embed(any())).thenReturn(new EmbeddingResult("embed", ones(768), 4));
        when(symptomMatch.match(any())).thenReturn(new SymptomMatchResult(List.of(), List.of(), true));
        VoiceKbLookupResult result = handler.handle(command("bn"));
        assertThat(result.inconclusive()).isFalse();
        assertThat(result.transcription()).isEqualTo("blast");
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().diseaseId()).isEqualTo(BLAST);
        assertThat(result.candidates().getFirst().matcher()).isEqualTo(VoiceKbMatchers.NAME);
        verify(knowledge, never()).listActiveRemedies(any());
    }

    @Test
    void symptomHitIsMergedWhenNameMisses() {
        stubCrop();
        stubCatalogue();
        when(speech.transcribeAudio(any(), eq("bn"), eq("corr")))
                .thenReturn(new TranscriptResult("asr", "পাতায় দাগ", new BigDecimal("0.800"), 12));
        when(embedding.embed(any())).thenReturn(new EmbeddingResult("embed", ones(768), 4));
        when(symptomMatch.match(any()))
                .thenReturn(new SymptomMatchResult(
                        List.of(new MatchedSymptom(
                                UUID.fromString("01800000-0000-7000-8000-000000005001"),
                                "LEAF_SPOT",
                                "দাগ",
                                new BigDecimal("0.880"),
                                VoiceKbMatchers.VECTOR)),
                        List.of(new ScoredDisease(BROWN, "BROWN_SPOT", "বাদামি দাগ", new BigDecimal("0.770"), 1)),
                        false));
        VoiceKbLookupResult result = handler.handle(command("bn"));
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().diseaseId()).isEqualTo(BROWN);
        assertThat(result.candidates().getFirst().matcher()).isEqualTo(VoiceKbMatchers.VECTOR);
    }

    @Test
    void wrongEmbeddingDimensionDoesNotCallMatcher() {
        stubCrop();
        stubCatalogue();
        when(speech.transcribeAudio(any(), eq("bn"), eq("corr")))
                .thenReturn(new TranscriptResult("asr", "blast", new BigDecimal("0.900"), 12));
        when(embedding.embed(any())).thenReturn(new EmbeddingResult("embed", ones(512), 4));
        assertThatThrownBy(() -> handler.handle(command("bn")))
                .isInstanceOf(AnalysisException.class)
                .extracting(ex -> ((AnalysisException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_EMBEDDING_DIMENSION);
        verify(symptomMatch, never()).match(any());
    }

    @Test
    void mapsSidecarUndecodableAudio() {
        stubCrop();
        when(speech.transcribeAudio(any(), any(), any()))
                .thenThrow(new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNDECODABLE, "bad wav"));
        assertThatThrownBy(() -> handler.handle(command("bn")))
                .isInstanceOf(AnalysisException.class)
                .extracting(ex -> ((AnalysisException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_SIDECAR_UNDECODABLE);
    }

    @Test
    void unmappableSpeechIsInconclusive() {
        stubCrop();
        stubCatalogue();
        when(speech.transcribeAudio(any(), eq("bn"), eq("corr")))
                .thenReturn(new TranscriptResult("asr", "weather today", new BigDecimal("0.400"), 12));
        when(embedding.embed(any())).thenReturn(new EmbeddingResult("embed", ones(768), 4));
        when(symptomMatch.match(any())).thenReturn(new SymptomMatchResult(List.of(), List.of(), true));
        VoiceKbLookupResult result = handler.handle(command("bn"));
        assertThat(result.inconclusive()).isTrue();
        assertThat(result.candidates()).isEmpty();
        assertThat(result.transcription()).isEqualTo("weather today");
    }

    private void stubCrop() {
        when(knowledge.findCropById(CROP)).thenReturn(Optional.of(crop()));
    }

    private void stubCatalogue() {
        when(knowledge.listDiseasesByCrop(CROP))
                .thenReturn(List.of(
                        new DiseaseView(BLAST, CROP, "BLAST", "ব্লাস্ট", "Blast", "", Severity.HIGH, false),
                        new DiseaseView(
                                BROWN, CROP, "BROWN_SPOT", "বাদামি দাগ", "Brown spot", "", Severity.MODERATE, false)));
    }

    private static LookupVoiceKbCommand command(String language) {
        return new LookupVoiceKbCommand(CROP, new byte[] {1, 2, 3}, language, "corr");
    }

    private static CropView crop() {
        return new CropView(CROP, "RICE", "ধান", "Rice", "rice");
    }

    private static float[] ones(int dimension) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = 0.1f;
        }
        return vector;
    }
}

package com.rootcause.foshol.analysis.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.application.AnalysisSettings;
import com.rootcause.foshol.analysis.application.command.RunAnalysisCommand;
import com.rootcause.foshol.analysis.application.port.AnalysisEventPort;
import com.rootcause.foshol.analysis.application.port.AnalysisPersistencePort;
import com.rootcause.foshol.analysis.application.port.EmbeddingResult;
import com.rootcause.foshol.analysis.application.port.ExplainabilityPort;
import com.rootcause.foshol.analysis.application.port.ExplanationResult;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TextEmbeddingPort;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.application.port.VisionModelPort;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.domain.AnalysisRun;
import com.rootcause.foshol.analysis.domain.CaseCandidate;
import com.rootcause.foshol.analysis.domain.CaseSymptom;
import com.rootcause.foshol.common.CandidateSource;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;
import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import com.rootcause.foshol.common.Severity;
import com.rootcause.foshol.common.SymptomSource;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.MatchedSymptom;
import com.rootcause.foshol.knowledge.api.RemedyView;
import com.rootcause.foshol.knowledge.api.ScoredDisease;
import com.rootcause.foshol.knowledge.api.SymptomMatchApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class RunAnalysisCommandHandlerTest {

    static final UUID CASE_ID = UUID.fromString("01800000-0000-7000-8000-00000000c001");
    static final UUID FARMER = UUID.fromString("01800000-0000-7000-8000-00000000f001");
    static final UUID CROP = UUID.fromString("01800000-0000-7000-8000-000000000001");
    static final UUID DISEASE = UUID.fromString("01800000-0000-7000-8000-000000000101");
    static final UUID IMAGE_A = UUID.fromString("01800000-0000-7000-8000-00000000aa01");
    static final UUID IMAGE_B = UUID.fromString("01800000-0000-7000-8000-00000000aa02");
    static final String HIGH_SHA = "8b3b8b23ce56af5cc3f864cf5fbca0f46b786901ec423237692c396cee9d1599";
    static final String HIGH_B_SHA = "9ee38e5617b0f15aed8951c05cc2de934295d5209a7bcfda86d0a589272f3dd9";

    @Mock
    CaseIntakeApi intake;
    @Mock
    KnowledgeQueryApi knowledge;
    @Mock
    SymptomMatchApi symptomMatch;
    @Mock
    VisionModelPort vision;
    @Mock
    SpeechToTextPort speech;
    @Mock
    TextEmbeddingPort embedding;
    @Mock
    ExplainabilityPort explainability;
    @Mock
    ObjectStorePort objectStore;
    @Mock
    AnalysisPersistencePort persistence;
    @Mock
    AnalysisEventPort events;

    RunAnalysisCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RunAnalysisCommandHandler(
                intake,
                knowledge,
                symptomMatch,
                vision,
                speech,
                embedding,
                explainability,
                objectStore,
                persistence,
                events,
                settings(Duration.ofSeconds(3), true),
                new SimpleMeterRegistry());
    }

    @Test
    void skipsWhenCompletedRunExists() {
        when(persistence.hasCompletedRun(CASE_ID)).thenReturn(true);
        handler.handle(command(null));
        verify(intake, never()).findById(any());
        verify(events, never()).publishCompleted(any());
    }

    @Test
    void publishesFailedWhenCaseMissing() {
        when(persistence.hasCompletedRun(CASE_ID)).thenReturn(false);
        when(intake.findById(CASE_ID)).thenReturn(Optional.empty());
        handler.handle(command(null));
        ArgumentCaptor<AnalysisFailed> captor = ArgumentCaptor.forClass(AnalysisFailed.class);
        verify(events).publishFailed(captor.capture());
        assertThat(captor.getValue().errorCode()).isEqualTo(ErrorCodes.ERR_CASE_NOT_FOUND);
        verify(persistence, never()).saveNewRun(any(), any(), any());
    }

    @Test
    void primaryPathPersistsOneRunAndCompletes() {
        stubHappyVision();
        when(intake.findById(CASE_ID)).thenReturn(Optional.of(summary(null)));
        when(persistence.hasCompletedRun(CASE_ID)).thenReturn(false);
        when(explainability.explain(any()))
                .thenReturn(new ExplanationResult("m", "v", new byte[] {1, 2, 3}, "image/png", 1));
        handler.handle(command(null));
        ArgumentCaptor<AnalysisRun> run = ArgumentCaptor.forClass(AnalysisRun.class);
        verify(persistence).saveNewRun(run.capture(), any(), any());
        assertThat(run.getValue().decisionPath()).isEqualTo(DecisionPath.PRIMARY);
        verify(events).publishCompleted(any(AnalysisCompleted.class));
        verify(speech, never()).transcribe(any());
    }

    @Test
    void missingFixtureDegradesToUndetermined() {
        when(intake.findById(CASE_ID)).thenReturn(Optional.of(summary(null)));
        when(persistence.hasCompletedRun(CASE_ID)).thenReturn(false);
        when(vision.classify(any()))
                .thenThrow(new SidecarFailureException(ErrorCodes.ERR_FIXTURE_MISSING, "missing"));
        handler.handle(command(null));
        ArgumentCaptor<AnalysisRun> run = ArgumentCaptor.forClass(AnalysisRun.class);
        verify(persistence).saveNewRun(run.capture(), any(), any());
        assertThat(run.getValue().decisionPath()).isEqualTo(DecisionPath.UNDETERMINED);
        assertThat(run.getValue().errorCode()).isEqualTo(ErrorCodes.ERR_FIXTURE_MISSING);
        verify(events).publishCompleted(any());
        verify(events, never()).publishFailed(any());
    }

    @Test
    void allUnmappedLabelsAreUndetermined() {
        when(intake.findById(CASE_ID)).thenReturn(Optional.of(summary(null)));
        when(persistence.hasCompletedRun(CASE_ID)).thenReturn(false);
        when(vision.classify(any())).thenReturn(new VisionResult(
                "model",
                "v1",
                List.of(new com.rootcause.foshol.analysis.application.port.RawCandidate(
                        "unknown-label", new BigDecimal("0.9100"))),
                12));
        when(knowledge.resolveModelLabel(any(), any(), any())).thenReturn(Optional.empty());
        handler.handle(command(null));
        ArgumentCaptor<AnalysisRun> run = ArgumentCaptor.forClass(AnalysisRun.class);
        verify(persistence).saveNewRun(run.capture(), any(), any());
        assertThat(run.getValue().decisionPath()).isEqualTo(DecisionPath.UNDETERMINED);
        assertThat(run.getValue().errorCode()).isEqualTo(ErrorCodes.ERR_ALL_LABELS_UNMAPPED);
        verify(events).publishCompleted(any());
        verify(events, never()).publishFailed(any());
    }

    @Test
    void sidecarUnavailableStillCompletes() {
        when(intake.findById(CASE_ID)).thenReturn(Optional.of(summary(null)));
        when(persistence.hasCompletedRun(CASE_ID)).thenReturn(false);
        when(vision.classify(any()))
                .thenThrow(new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "down"));
        handler.handle(command(null));
        ArgumentCaptor<AnalysisRun> run = ArgumentCaptor.forClass(AnalysisRun.class);
        verify(persistence).saveNewRun(run.capture(), any(), any());
        assertThat(run.getValue().decisionPath()).isEqualTo(DecisionPath.UNDETERMINED);
        assertThat(run.getValue().errorCode()).isEqualTo(ErrorCodes.ERR_SIDECAR_UNAVAILABLE);
        verify(events).publishCompleted(any());
        verify(events, never()).publishFailed(any());
    }

    @Test
    void speechTimeoutAbandonsSpeechAndKeepsVision() throws Exception {
        handler = new RunAnalysisCommandHandler(
                intake,
                knowledge,
                symptomMatch,
                vision,
                speech,
                embedding,
                explainability,
                objectStore,
                persistence,
                events,
                settings(Duration.ofMillis(250), true),
                new SimpleMeterRegistry());
        stubHappyVision();
        CaseAudioRef audio = new CaseAudioRef(UUID.randomUUID(), "audio.wav", 1000, null);
        when(intake.findById(CASE_ID)).thenReturn(Optional.of(summary(audio)));
        when(persistence.hasCompletedRun(CASE_ID)).thenReturn(false);
        when(objectStore.read(any())).thenReturn("fixture:asr:demo".getBytes());
        when(speech.transcribe(any())).thenAnswer(inv -> {
            Thread.sleep(1_500);
            return null;
        });
        when(explainability.explain(any()))
                .thenReturn(new ExplanationResult("m", "v", new byte[] {1}, "image/png", 1));
        handler.handle(command(audio));
        ArgumentCaptor<AnalysisRun> run = ArgumentCaptor.forClass(AnalysisRun.class);
        verify(persistence).saveNewRun(run.capture(), any(), any());
        assertThat(run.getValue().errorCode()).isEqualTo(ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT);
        assertThat(run.getValue().decisionPath()).isEqualTo(DecisionPath.PRIMARY);
        verify(events).publishCompleted(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void secondaryPathMergesVisionAndKnowledge() {
        when(vision.classify(any())).thenReturn(new VisionResult(
                "kssrikar4/Rice-Leaf-Disease-Classification",
                "02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26",
                List.of(new com.rootcause.foshol.analysis.application.port.RawCandidate(
                        "Brown Spot", new BigDecimal("0.6000"))),
                12));
        when(knowledge.resolveModelLabel(any(), any(), any())).thenReturn(Optional.of(DISEASE));
        when(knowledge.findDiseaseById(DISEASE)).thenReturn(Optional.of(new DiseaseView(
                DISEASE, CROP, "brown_spot", "Brown spot", "Brown spot", null, Severity.LOW, false)));
        when(knowledge.listActiveRemedies(DISEASE)).thenReturn(List.of(new RemedyView(
                UUID.randomUUID(), DISEASE, com.rootcause.foshol.common.RemedyType.CULTURAL,
                "TODO(content-owner)", List.of("TODO(content-owner)"), null, null, "LOW", "HIGH", "TODO(content-owner)")));
        CaseAudioRef audio = new CaseAudioRef(UUID.randomUUID(), "audio.wav", 1000, null);
        when(intake.findById(CASE_ID)).thenReturn(Optional.of(summary(audio)));
        when(persistence.hasCompletedRun(CASE_ID)).thenReturn(false);
        when(objectStore.read(any())).thenReturn("fixture:asr:demo".getBytes());
        when(speech.transcribe(any()))
                .thenReturn(new TranscriptResult("asr", "TODO(content-owner)", new BigDecimal("0.91"), 20));
        float[] embeddingVector = new float[768];
        embeddingVector[0] = 0.1f;
        when(embedding.embed(any())).thenReturn(new EmbeddingResult("embed", embeddingVector, 1));
        UUID symptomId = UUID.fromString("01800000-0000-7000-8000-000000005001");
        when(symptomMatch.match(any())).thenReturn(new SymptomMatchResult(
                List.of(new MatchedSymptom(
                        symptomId, "s", "TODO(content-owner)", new BigDecimal("0.812"), "VECTOR")),
                List.of(new ScoredDisease(DISEASE, "brown_spot", "Brown spot", new BigDecimal("0.70"), 1)),
                false));
        when(explainability.explain(any()))
                .thenReturn(new ExplanationResult("m", "v", new byte[] {1}, "image/png", 1));
        handler.handle(command(audio));
        ArgumentCaptor<AnalysisRun> run = ArgumentCaptor.forClass(AnalysisRun.class);
        ArgumentCaptor<List<CaseCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<CaseSymptom>> symptoms = ArgumentCaptor.forClass(List.class);
        verify(persistence).saveNewRun(run.capture(), candidates.capture(), symptoms.capture());
        assertThat(run.getValue().decisionPath()).isEqualTo(DecisionPath.SECONDARY);
        assertThat(candidates.getValue())
                .extracting(CaseCandidate::source)
                .contains(CandidateSource.MODEL, CandidateSource.KB, CandidateSource.MERGED);
        assertThat(symptoms.getValue()).isNotEmpty();
        assertThat(symptoms.getValue().get(0).source()).isEqualTo(SymptomSource.SPEECH);
        verify(events).publishCompleted(any(AnalysisCompleted.class));
    }

    private void stubHappyVision() {
        when(vision.classify(any())).thenReturn(new VisionResult(
                "kssrikar4/Rice-Leaf-Disease-Classification",
                "02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26",
                List.of(new com.rootcause.foshol.analysis.application.port.RawCandidate(
                        "Brown Spot", new BigDecimal("0.9100"))),
                12));
        when(knowledge.resolveModelLabel(any(), any(), any())).thenReturn(Optional.of(DISEASE));
        when(knowledge.findDiseaseById(DISEASE)).thenReturn(Optional.of(new DiseaseView(
                DISEASE, CROP, "brown_spot", "Brown spot", "Brown spot", null, Severity.LOW, false)));
        when(knowledge.listActiveRemedies(DISEASE)).thenReturn(List.of(new RemedyView(
                UUID.randomUUID(), DISEASE, com.rootcause.foshol.common.RemedyType.CULTURAL,
                "t", List.of("s"), null, null, "LOW", "HIGH", "ref")));
    }

    private RunAnalysisCommand command(CaseAudioRef audio) {
        return new RunAnalysisCommand(CASE_ID, FARMER, CROP, "rice", images(), audio, "corr-1");
    }

    private CaseSummary summary(CaseAudioRef audio) {
        return new CaseSummary(
                CASE_ID,
                FARMER,
                CROP,
                "rice",
                "BD-13",
                com.rootcause.foshol.common.CaseStatus.SUBMITTED,
                null,
                null,
                null,
                images(),
                audio,
                "corr-1",
                Instant.now());
    }

    private static List<CaseImageRef> images() {
        return List.of(
                new CaseImageRef(IMAGE_A, "img-a", null, HIGH_SHA, new BigDecimal("0.900"), true, 1),
                new CaseImageRef(IMAGE_B, "img-b", null, HIGH_B_SHA, new BigDecimal("0.600"), false, 2));
    }

    static AnalysisSettings settings(Duration deadline, boolean gradcam) {
        return new AnalysisSettings(
                "replay",
                new BigDecimal("0.75"),
                new BigDecimal("0.45"),
                BigDecimal.ONE,
                "MAX",
                deadline,
                5,
                gradcam,
                "http://localhost:8000",
                Duration.ofSeconds(8),
                Duration.ofMinutes(10),
                "http://localhost:9000",
                "minio",
                "minio12345",
                "foshol-cases");
    }
}

package com.rootcause.foshol.analysis.application.command.handler;

import com.rootcause.foshol.analysis.application.AnalysisSettings;
import com.rootcause.foshol.analysis.application.command.RunAnalysisCommand;
import com.rootcause.foshol.analysis.application.port.AnalysisEventPort;
import com.rootcause.foshol.analysis.application.port.AnalysisPersistencePort;
import com.rootcause.foshol.analysis.application.port.EmbeddingRequest;
import com.rootcause.foshol.analysis.application.port.EmbeddingResult;
import com.rootcause.foshol.analysis.application.port.ExplainabilityPort;
import com.rootcause.foshol.analysis.application.port.ExplanationRequest;
import com.rootcause.foshol.analysis.application.port.ExplanationResult;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TextEmbeddingPort;
import com.rootcause.foshol.analysis.application.port.TranscriptRequest;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.application.port.VisionModelPort;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.domain.AnalysisRun;
import com.rootcause.foshol.analysis.domain.AnalysisScale;
import com.rootcause.foshol.analysis.domain.BranchOutcome;
import com.rootcause.foshol.analysis.domain.CandidateAggregator;
import com.rootcause.foshol.analysis.domain.CaseCandidate;
import com.rootcause.foshol.analysis.domain.CaseSymptom;
import com.rootcause.foshol.analysis.domain.ConfidenceRouter;
import com.rootcause.foshol.analysis.domain.LabelResolver;
import com.rootcause.foshol.analysis.domain.MappedCandidate;
import com.rootcause.foshol.analysis.domain.MergeRanker;
import com.rootcause.foshol.analysis.domain.PrescribableSpec;
import com.rootcause.foshol.analysis.domain.RankedCandidate;
import com.rootcause.foshol.analysis.domain.RawCandidate;
import com.rootcause.foshol.analysis.domain.RoutingDecision;
import com.rootcause.foshol.analysis.domain.ScoredDiseaseScore;
import com.rootcause.foshol.analysis.domain.TemperatureScaler;
import com.rootcause.foshol.common.BanglaNormalizer;
import com.rootcause.foshol.common.CandidateSource;
import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.DecisionPath;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;
import com.rootcause.foshol.common.events.CandidateView;
import com.rootcause.foshol.common.events.CaseAudioRef;
import com.rootcause.foshol.common.events.CaseImageRef;
import com.rootcause.foshol.common.events.SymptomView;
import com.rootcause.foshol.common.SymptomSource;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.intake.api.CaseIntakeApi;
import com.rootcause.foshol.intake.api.CaseSummary;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.MatchedSymptom;
import com.rootcause.foshol.knowledge.api.ScoredDisease;
import com.rootcause.foshol.knowledge.api.SymptomMatchApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchRequest;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class RunAnalysisCommandHandler implements CommandHandler<RunAnalysisCommand, Void> {

    @Override
    public Class<RunAnalysisCommand> commandType() {
        return RunAnalysisCommand.class;
    }

    private static final Logger log = LoggerFactory.getLogger(RunAnalysisCommandHandler.class);
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String METRIC_PATH = "foshol.analysis.path";

    private final CaseIntakeApi intake;
    private final KnowledgeQueryApi knowledge;
    private final SymptomMatchApi symptomMatch;
    private final VisionModelPort vision;
    private final SpeechToTextPort speech;
    private final TextEmbeddingPort embedding;
    private final ExplainabilityPort explainability;
    private final ObjectStorePort objectStore;
    private final AnalysisPersistencePort persistence;
    private final AnalysisEventPort events;
    private final AnalysisSettings settings;
    private final MeterRegistry meters;
    private final CandidateAggregator aggregator = new CandidateAggregator();
    private final LabelResolver labelResolver = new LabelResolver();

    public RunAnalysisCommandHandler(
            CaseIntakeApi intake,
            KnowledgeQueryApi knowledge,
            SymptomMatchApi symptomMatch,
            VisionModelPort vision,
            SpeechToTextPort speech,
            TextEmbeddingPort embedding,
            ExplainabilityPort explainability,
            ObjectStorePort objectStore,
            AnalysisPersistencePort persistence,
            AnalysisEventPort events,
            AnalysisSettings settings,
            MeterRegistry meters) {
        this.intake = intake;
        this.knowledge = knowledge;
        this.symptomMatch = symptomMatch;
        this.vision = vision;
        this.speech = speech;
        this.embedding = embedding;
        this.explainability = explainability;
        this.objectStore = objectStore;
        this.persistence = persistence;
        this.events = events;
        this.settings = settings;
        this.meters = meters;
    }

    @Override
    public Void handle(RunAnalysisCommand command) {
        CorrelationId.set(command.correlationId());
        Instant start = Instant.now();
        try {
            if (persistence.hasCompletedRun(command.caseId())) {
                return null;
            }
            Optional<CaseSummary> loaded = intake.findById(command.caseId());
            if (loaded.isEmpty()) {
                events.publishFailed(new AnalysisFailed(
                        command.caseId(),
                        command.farmerId(),
                        ErrorCodes.ERR_CASE_NOT_FOUND,
                        command.correlationId(),
                        Instant.now()));
                return null;
            }
            CaseSummary summary = loaded.get();
            Assembled assembled = orchestrate(command, summary, start);
            persistence.saveNewRun(assembled.run(), assembled.candidates(), assembled.speechSymptoms());
            try {
                if (assembled.transcriptBn() != null) {
                    intake.recordTranscript(command.caseId(), assembled.transcriptBn(), assembled.asrConfidence());
                }
            } catch (RuntimeException transcriptEx) {
                log.warn(
                        "transcript persist failed after analysis_run caseId={} correlationId={}",
                        command.caseId(),
                        command.correlationId(),
                        transcriptEx);
            }
            events.publishCompleted(assembled.completed());
            meters.counter(METRIC_PATH, "path", assembled.run().decisionPath().name()).increment();
        } catch (RuntimeException ex) {
            log.warn(
                    "analysis persistence failed caseId={} correlationId={}",
                    command.caseId(),
                    command.correlationId(),
                    ex);
            events.publishFailed(new AnalysisFailed(
                    command.caseId(),
                    command.farmerId(),
                    ErrorCodes.ERR_ANALYSIS_NOT_FOUND,
                    command.correlationId(),
                    Instant.now()));
        } finally {
            CorrelationId.clear();
        }
        return null;
    
}

    Assembled orchestrate(RunAnalysisCommand command, CaseSummary summary, Instant start) {
        Duration deadline = settings.deadline();
        VisionBundle visionBundle;
        SpeechBundle speechBundle;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            CompletableFuture<VisionBundle> visionFuture =
                    CompletableFuture.supplyAsync(() -> runVision(command, summary), executor);
            CompletableFuture<SpeechBundle> speechFuture =
                    CompletableFuture.supplyAsync(() -> runSpeech(command, summary), executor);
            try {
                CompletableFuture.allOf(visionFuture, speechFuture)
                        .get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                abandon(visionFuture, speechFuture, command.correlationId());
            } catch (Exception interrupted) {
                Thread.currentThread().interrupt();
                abandon(visionFuture, speechFuture, command.correlationId());
            }
            visionBundle = completedOrAbandoned(visionFuture);
            speechBundle = completedOrAbandonedSpeech(speechFuture);
        } finally {
            executor.shutdownNow();
        }
        return assemble(command, summary, visionBundle, speechBundle, start);
    }

    private void abandon(
            CompletableFuture<VisionBundle> visionFuture,
            CompletableFuture<SpeechBundle> speechFuture,
            String correlationId) {
        if (!visionFuture.isDone()) {
            visionFuture.cancel(true);
            log.warn("vision branch abandoned correlationId={}", correlationId);
        }
        if (!speechFuture.isDone()) {
            speechFuture.cancel(true);
            log.warn("speech branch abandoned correlationId={}", correlationId);
        }
    }

    private VisionBundle completedOrAbandoned(CompletableFuture<VisionBundle> future) {
        if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
            return future.getNow(VisionBundle.abandoned());
        }
        SidecarFailureException sidecar = sidecarIfFailed(future);
        if (sidecar != null) {
            return VisionBundle.failed(sidecar.errorCode());
        }
        return VisionBundle.abandoned();
    }

    private SpeechBundle completedOrAbandonedSpeech(CompletableFuture<SpeechBundle> future) {
        if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
            return future.getNow(SpeechBundle.abandoned(ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT));
        }
        SidecarFailureException sidecar = sidecarIfFailed(future);
        if (sidecar != null) {
            return SpeechBundle.failed(sidecar.errorCode());
        }
        return SpeechBundle.abandoned(ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT);
    }

    private static SidecarFailureException sidecarIfFailed(CompletableFuture<?> future) {
        if (!future.isCompletedExceptionally() || future.isCancelled()) {
            return null;
        }
        try {
            future.join();
            return null;
        } catch (Exception ex) {
            if (isCancellation(ex) || isInterrupted(ex)) {
                return null;
            }
            return findSidecar(ex);
        }
    }

    private VisionBundle runVision(RunAnalysisCommand command, CaseSummary summary) {
        List<CaseImageRef> images = summary.images() == null ? List.of() : summary.images();
        List<List<MappedCandidate>> perImage = new ArrayList<>();
        List<ImageRaw> raws = new ArrayList<>();
        Set<String> unmapped = new LinkedHashSet<>();
        String modelId = null;
        String modelVersion = null;
        String primaryRawLabel = null;
        CaseImageRef primary = primaryImage(images);
        try {
            for (CaseImageRef image : images) {
                VisionResult result = vision.classify(new VisionRequest(
                        command.caseId(),
                        image.imageId(),
                        summary.cropCode(),
                        image.objectKey(),
                        image.sha256(),
                        command.correlationId()));
                modelId = result.modelId();
                modelVersion = result.modelVersion();
                List<RawCandidate> scaled = TemperatureScaler.rescale(toDomain(result.candidates()), settings.temperature());
                if (primary != null && image.imageId().equals(primary.imageId()) && !scaled.isEmpty()) {
                    primaryRawLabel = scaled.get(0).rawLabel();
                    BigDecimal best = scaled.get(0).confidence();
                    for (RawCandidate c : scaled) {
                        if (c.confidence().compareTo(best) > 0) {
                            best = c.confidence();
                            primaryRawLabel = c.rawLabel();
                        }
                    }
                }
                String reportedId = result.modelId();
                String reportedVersion = result.modelVersion();
                LabelResolver.Resolution resolution = labelResolver.resolve(
                        scaled,
                        reportedId,
                        reportedVersion,
                        knowledge::resolveModelLabel,
                        id -> knowledge.findDiseaseById(id).map(DiseaseView::code).orElse(id.toString()),
                        command.correlationId());
                unmapped.addAll(resolution.unmappedLabels());
                perImage.add(aggregator.maxWithinImage(resolution.mapped()));
                raws.add(new ImageRaw(image, result, scaled));
            }
            List<MappedCandidate> aggregated = aggregator.aggregateAcrossImages(
                    perImage, settings.aggregation(), settings.candidateLimit());
            String gradcamKey = storeGradcam(command, summary, primary, primaryRawLabel, aggregated);
            return new VisionBundle(
                    BranchOutcome.COMPLETED, null, modelId, modelVersion, aggregated, List.copyOf(unmapped), raws, primary, gradcamKey);
        } catch (SidecarFailureException ex) {
            return VisionBundle.failed(ex.errorCode());
        } catch (RuntimeException ex) {
            SidecarFailureException sidecar = findSidecar(ex);
            if (sidecar != null) {
                return VisionBundle.failed(sidecar.errorCode());
            }
            throw ex;
        }
    }

    private SpeechBundle runSpeech(RunAnalysisCommand command, CaseSummary summary) {
        CaseAudioRef audio = summary.audio();
        if (audio == null) {
            return SpeechBundle.empty();
        }
        try {
            byte[] bytes = objectStore.read(audio.objectKey());
            String sha256 = sha256Hex(bytes);
            TranscriptResult transcript = speech.transcribe(new TranscriptRequest(
                    command.caseId(),
                    audio.audioId(),
                    audio.objectKey(),
                    sha256,
                    audio.durationMs(),
                    command.correlationId()));
            String normalised = BanglaNormalizer.forStorage(transcript.transcriptBn());
            EmbeddingResult embedded = embedding.embed(
                    new EmbeddingRequest(command.caseId(), normalised, command.correlationId()));
            float[] vector = embedded.vector();
            if (vector == null || vector.length == 0 || isAllZeros(vector)) {
                return new SpeechBundle(
                        BranchOutcome.COMPLETED,
                        null,
                        transcript.modelId(),
                        embedded.modelId(),
                        normalised,
                        transcript.asrConfidence(),
                        null,
                        audio.audioId());
            }
            if (vector.length != AnalysisScale.EMBEDDING_DIMENSION) {
                log.warn(
                        "embedding dimension invalid caseId={} correlationId={}",
                        command.caseId(),
                        command.correlationId());
                return SpeechBundle.failed(ErrorCodes.ERR_EMBEDDING_DIMENSION);
            }
            SymptomMatchResult match = symptomMatch.match(new SymptomMatchRequest(
                    summary.cropId(), normalised, vector, List.of()));
            return new SpeechBundle(
                    BranchOutcome.COMPLETED,
                    null,
                    transcript.modelId(),
                    embedded.modelId(),
                    normalised,
                    transcript.asrConfidence(),
                    match,
                    audio.audioId());
        } catch (SidecarFailureException ex) {
            if (ErrorCodes.ERR_FIXTURE_MISSING.equals(ex.errorCode())) {
                return SpeechBundle.abandoned(ex.errorCode());
            }
            return SpeechBundle.failed(ex.errorCode());
        } catch (RuntimeException ex) {
            SidecarFailureException sidecar = findSidecar(ex);
            if (sidecar != null) {
                if (ErrorCodes.ERR_FIXTURE_MISSING.equals(sidecar.errorCode())) {
                    return SpeechBundle.abandoned(sidecar.errorCode());
                }
                return SpeechBundle.failed(sidecar.errorCode());
            }
            throw ex;
        }
    }

    private Assembled assemble(
            RunAnalysisCommand command,
            CaseSummary summary,
            VisionBundle visionBundle,
            SpeechBundle speechBundle,
            Instant start) {
        List<MappedCandidate> visionCandidates =
                visionBundle.aggregated() == null ? List.of() : visionBundle.aggregated();
        boolean kbInconclusive = speechBundle.outcome() != BranchOutcome.COMPLETED
                || speechBundle.match() == null
                || speechBundle.match().inconclusive();
        boolean allUnmapped = visionBundle.outcome() == BranchOutcome.COMPLETED
                && visionCandidates.isEmpty()
                && visionBundle.unmapped() != null
                && !visionBundle.unmapped().isEmpty();
        BigDecimal top1 = visionCandidates.isEmpty() ? null : AnalysisScale.confidence(visionCandidates.get(0).confidence());
        BigDecimal top2 = visionCandidates.size() < 2
                ? null
                : AnalysisScale.confidence(visionCandidates.get(1).confidence());
        boolean prescribable = false;
        if (top1 != null) {
            UUID diseaseId = visionCandidates.get(0).diseaseId();
            boolean healthy = knowledge.findDiseaseById(diseaseId).map(DiseaseView::healthy).orElse(false);
            boolean remedies = !knowledge.listActiveRemedies(diseaseId).isEmpty();
            prescribable = PrescribableSpec.isSatisfied(healthy, remedies);
        }
        RoutingDecision routed = ConfidenceRouter.route(
                top1, top2, prescribable, kbInconclusive, settings.confidenceHigh(), settings.confidenceLow());
        String error = firstError(visionBundle, speechBundle, allUnmapped, routed);
        DecisionPath path = routed.path();
        if (sidecarUnavailable(visionBundle, speechBundle) || allUnmapped || bothFailed(visionBundle, speechBundle)) {
            path = DecisionPath.UNDETERMINED;
        }
        if (allUnmapped) {
            error = ErrorCodes.ERR_ALL_LABELS_UNMAPPED;
        }
        if (sidecarUnavailable(visionBundle, speechBundle)
                && (error == null
                        || ErrorCodes.ERR_VISION_BRANCH_TIMEOUT.equals(error)
                        || ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT.equals(error))) {
            error = sidecarError(visionBundle, speechBundle);
        }
        if (visionBundle.outcome() == BranchOutcome.ABANDONED && error == null) {
            error = ErrorCodes.ERR_VISION_BRANCH_TIMEOUT;
        }
        if (speechBundle.outcome() == BranchOutcome.ABANDONED
                && summary.audio() != null
                && (error == null || path == DecisionPath.UNDETERMINED && visionBundle.outcome() == BranchOutcome.COMPLETED)) {
            if (error == null) {
                error = ErrorCodes.ERR_SPEECH_BRANCH_TIMEOUT;
            }
        }
        List<CaseCandidate> persisted = new ArrayList<>();
        List<MappedCandidate> eventCandidates = visionCandidates;
        if (path == DecisionPath.SECONDARY && speechBundle.match() != null) {
            List<MappedCandidate> kbMapped = kbMapped(speechBundle.match());
            Map<UUID, String> codes = new LinkedHashMap<>();
            for (MappedCandidate c : visionCandidates) {
                codes.put(c.diseaseId(), c.diseaseCode());
            }
            for (MappedCandidate c : kbMapped) {
                codes.put(c.diseaseId(), c.diseaseCode());
            }
            List<RankedCandidate> visionRanked = rank(visionCandidates, CandidateSource.MODEL);
            List<ScoredDiseaseScore> kbScores = new ArrayList<>();
            for (MappedCandidate c : kbMapped) {
                kbScores.add(new ScoredDiseaseScore(c.diseaseId(), c.confidence(), c.diseaseCode()));
            }
            List<RankedCandidate> merged =
                    MergeRanker.merge(visionRanked, kbScores, codes, settings.candidateLimit());
            persisted.addAll(toEntities(command.caseId(), visionCandidates, CandidateSource.MODEL));
            persisted.addAll(toEntities(command.caseId(), kbMapped, CandidateSource.KB));
            persisted.addAll(toEntitiesFromRanked(command.caseId(), merged, CandidateSource.MERGED));
            eventCandidates = fromRanked(merged);
        } else if (!visionCandidates.isEmpty()) {
            persisted.addAll(toEntities(command.caseId(), visionCandidates, CandidateSource.MODEL));
        }
        List<CaseSymptom> speechSymptoms = speechSymptoms(command.caseId(), speechBundle);
        int latency = (int) Duration.between(start, Instant.now()).toMillis();
        AnalysisRun run = new AnalysisRun(Uuid7.create(), command.caseId(), settings.aiMode(), Instant.now());
        String rawOutput = rawOutputJson(visionBundle, speechBundle);
        run.complete(
                path,
                top1,
                top2,
                latency,
                error,
                visionBundle.unmapped() == null ? List.of() : visionBundle.unmapped(),
                rawOutput,
                visionBundle.modelId(),
                visionBundle.modelVersion(),
                speechBundle.asrModelId(),
                speechBundle.embedModelId(),
                visionBundle.gradcamKey());
        List<CandidateView> views = toViews(eventCandidates, path == DecisionPath.SECONDARY ? CandidateSource.MERGED : CandidateSource.MODEL);
        List<SymptomView> symptomViews = toSymptomViews(speechSymptoms, speechBundle);
        AnalysisCompleted completed = new AnalysisCompleted(
                command.caseId(),
                command.farmerId(),
                command.cropId(),
                path,
                settings.aiMode(),
                top1,
                run.margin(),
                views,
                symptomViews,
                summary.audio() != null,
                summary.images() == null ? 0 : summary.images().size(),
                command.correlationId(),
                Instant.now());
        return new Assembled(run, persisted, speechSymptoms, completed, speechBundle.transcriptBn(), speechBundle.asrConfidence());
    }

    private String storeGradcam(
            RunAnalysisCommand command,
            CaseSummary summary,
            CaseImageRef primary,
            String primaryRawLabel,
            List<MappedCandidate> aggregated) {
        if (!settings.gradcamEnabled() || primary == null || aggregated.isEmpty() || primaryRawLabel == null) {
            return null;
        }
        try {
            ExplanationResult overlay = explainability.explain(new ExplanationRequest(
                    command.caseId(),
                    primary.imageId(),
                    summary.cropCode(),
                    primary.objectKey(),
                    primary.sha256(),
                    primaryRawLabel,
                    command.correlationId()));
            if (overlay == null || overlay.overlayPng() == null) {
                return null;
            }
            String key = "cases/" + command.caseId() + "/gradcam/" + primary.imageId() + ".png";
            objectStore.write(key, overlay.overlayPng(), CONTENT_TYPE_PNG);
            return key;
        } catch (RuntimeException ex) {
            log.warn("gradcam failed correlationId={}", command.correlationId());
            return null;
        }
    }

    private static CaseImageRef primaryImage(List<CaseImageRef> images) {
        return images.stream()
                .sorted(Comparator.comparing((CaseImageRef image) ->
                                image.qualityScore() == null ? BigDecimal.ZERO : image.qualityScore())
                        .reversed()
                        .thenComparingInt(CaseImageRef::position))
                .findFirst()
                .orElse(null);
    }

    private static List<RawCandidate> toDomain(
            List<com.rootcause.foshol.analysis.application.port.RawCandidate> candidates) {
        if (candidates == null) {
            return List.of();
        }
        List<RawCandidate> out = new ArrayList<>(candidates.size());
        for (com.rootcause.foshol.analysis.application.port.RawCandidate c : candidates) {
            out.add(new RawCandidate(c.rawLabel(), c.confidence()));
        }
        return out;
    }

    private List<MappedCandidate> kbMapped(SymptomMatchResult match) {
        List<MappedCandidate> out = new ArrayList<>();
        if (match.diseases() == null) {
            return out;
        }
        for (ScoredDisease d : match.diseases()) {
            out.add(new MappedCandidate(d.diseaseId(), d.code(), AnalysisScale.confidence(d.score())));
        }
        return aggregator.aggregateAcrossImages(List.of(out), settings.aggregation(), settings.candidateLimit());
    }

    private static List<RankedCandidate> rank(List<MappedCandidate> candidates, CandidateSource source) {
        List<RankedCandidate> out = new ArrayList<>();
        int rank = 1;
        for (MappedCandidate c : candidates) {
            out.add(new RankedCandidate(c.diseaseId(), c.confidence(), rank, c.diseaseCode()));
            rank++;
        }
        return out;
    }

    private static List<CaseCandidate> toEntities(
            UUID caseId, List<MappedCandidate> candidates, CandidateSource source) {
        List<CaseCandidate> out = new ArrayList<>();
        int rank = 1;
        for (MappedCandidate c : candidates) {
            out.add(new CaseCandidate(Uuid7.create(), caseId, c.diseaseId(), c.confidence(), rank, source));
            rank++;
        }
        return out;
    }

    private static List<CaseCandidate> toEntitiesFromRanked(
            UUID caseId, List<RankedCandidate> candidates, CandidateSource source) {
        List<CaseCandidate> out = new ArrayList<>();
        for (RankedCandidate c : candidates) {
            out.add(new CaseCandidate(Uuid7.create(), caseId, c.diseaseId(), c.confidence(), c.rank(), source));
        }
        return out;
    }

    private static List<MappedCandidate> fromRanked(List<RankedCandidate> ranked) {
        List<MappedCandidate> out = new ArrayList<>();
        for (RankedCandidate c : ranked) {
            out.add(new MappedCandidate(c.diseaseId(), c.diseaseCode(), c.confidence()));
        }
        return out;
    }

    private List<CandidateView> toViews(List<MappedCandidate> candidates, CandidateSource source) {
        List<CandidateView> out = new ArrayList<>();
        int rank = 1;
        for (MappedCandidate c : candidates) {
            String name = knowledge.findDiseaseById(c.diseaseId()).map(DiseaseView::nameBn).orElse(c.diseaseCode());
            out.add(new CandidateView(c.diseaseId(), c.diseaseCode(), name, c.confidence(), rank, source));
            rank++;
        }
        return out;
    }

    private List<CaseSymptom> speechSymptoms(UUID caseId, SpeechBundle speechBundle) {
        if (speechBundle.match() == null || speechBundle.match().symptoms() == null) {
            return List.of();
        }
        List<CaseSymptom> out = new ArrayList<>();
        for (MatchedSymptom s : speechBundle.match().symptoms()) {
            out.add(new CaseSymptom(
                    Uuid7.create(),
                    caseId,
                    s.symptomId(),
                    s.score(),
                    SymptomSource.SPEECH,
                    s.matcher()));
        }
        return out;
    }

    private List<SymptomView> toSymptomViews(List<CaseSymptom> symptoms, SpeechBundle speechBundle) {
        Map<UUID, MatchedSymptom> matched = new LinkedHashMap<>();
        if (speechBundle.match() != null && speechBundle.match().symptoms() != null) {
            for (MatchedSymptom s : speechBundle.match().symptoms()) {
                matched.put(s.symptomId(), s);
            }
        }
        List<SymptomView> out = new ArrayList<>();
        for (CaseSymptom s : symptoms) {
            MatchedSymptom m = matched.get(s.symptomId());
            out.add(new SymptomView(
                    s.symptomId(),
                    m == null ? s.symptomId().toString() : m.code(),
                    m == null ? "" : m.nameBn(),
                    s.score(),
                    s.source(),
                    s.matcher()));
        }
        return out;
    }

    private static String firstError(
            VisionBundle visionBundle, SpeechBundle speechBundle, boolean allUnmapped, RoutingDecision routed) {
        if (visionBundle.errorCode() != null) {
            return visionBundle.errorCode();
        }
        if (speechBundle.errorCode() != null
                && speechBundle.outcome() != BranchOutcome.COMPLETED
                && !ErrorCodes.ERR_FIXTURE_MISSING.equals(speechBundle.errorCode())) {
            return speechBundle.errorCode();
        }
        if (allUnmapped) {
            return ErrorCodes.ERR_ALL_LABELS_UNMAPPED;
        }
        return routed.errorCode();
    }

    private static boolean sidecarUnavailable(VisionBundle vision, SpeechBundle speech) {
        return ErrorCodes.ERR_SIDECAR_UNAVAILABLE.equals(vision.errorCode())
                || ErrorCodes.ERR_SIDECAR_UNAVAILABLE.equals(speech.errorCode())
                || ErrorCodes.ERR_FIXTURE_MISSING.equals(vision.errorCode());
    }

    private static String sidecarError(VisionBundle vision, SpeechBundle speech) {
        if (ErrorCodes.ERR_FIXTURE_MISSING.equals(vision.errorCode())
                || ErrorCodes.ERR_FIXTURE_MISSING.equals(speech.errorCode())) {
            return ErrorCodes.ERR_FIXTURE_MISSING;
        }
        return ErrorCodes.ERR_SIDECAR_UNAVAILABLE;
    }

    private static boolean bothFailed(VisionBundle vision, SpeechBundle speech) {
        return vision.outcome() != BranchOutcome.COMPLETED && speech.outcome() != BranchOutcome.COMPLETED;
    }

    private static boolean isCancellation(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.util.concurrent.CancellationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isInterrupted(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static SidecarFailureException findSidecar(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SidecarFailureException sidecar) {
                return sidecar;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean isAllZeros(float[] vector) {
        for (float value : vector) {
            if (value != 0.0f) {
                return false;
            }
        }
        return true;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String rawOutputJson(VisionBundle vision, SpeechBundle speech) {
        StringBuilder json = new StringBuilder();
        json.append("{\"schemaVersion\":1,\"images\":[");
        if (vision.raws() != null) {
            boolean first = true;
            for (ImageRaw raw : vision.raws()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"imageId\":\"")
                        .append(raw.image().imageId())
                        .append("\",\"sha256\":\"")
                        .append(raw.image().sha256())
                        .append("\",\"primary\":")
                        .append(vision.primary() != null && vision.primary().imageId().equals(raw.image().imageId()))
                        .append(",\"modelId\":\"")
                        .append(escape(raw.result().modelId()))
                        .append("\",\"modelVersion\":\"")
                        .append(escape(raw.result().modelVersion()))
                        .append("\",\"latencyMs\":")
                        .append(raw.result().latencyMs())
                        .append(",\"candidates\":[");
                boolean firstC = true;
                for (RawCandidate c : raw.scaled()) {
                    if (!firstC) {
                        json.append(',');
                    }
                    firstC = false;
                    json.append("{\"rawLabel\":\"")
                            .append(escape(c.rawLabel()))
                            .append("\",\"confidence\":")
                            .append(c.confidence())
                            .append('}');
                }
                json.append("]}");
            }
        }
        json.append("],\"speech\":");
        if (speech.transcriptBn() != null) {
            json.append("{\"audioId\":\"")
                    .append(speech.audioId())
                    .append("\",\"modelId\":\"")
                    .append(escape(speech.asrModelId()))
                    .append("\",\"transcriptBn\":\"")
                    .append(escape(speech.transcriptBn()))
                    .append("\",\"asrConfidence\":")
                    .append(speech.asrConfidence())
                    .append(",\"embedModelId\":\"")
                    .append(escape(speech.embedModelId()))
                    .append("\"}");
        } else {
            json.append("null");
        }
        json.append(",\"branches\":{\"vision\":\"")
                .append(vision.outcome())
                .append("\",\"speech\":\"")
                .append(speech.outcome())
                .append("\"}}");
        return json.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record ImageRaw(CaseImageRef image, VisionResult result, List<RawCandidate> scaled) {}

    record VisionBundle(
            BranchOutcome outcome,
            String errorCode,
            String modelId,
            String modelVersion,
            List<MappedCandidate> aggregated,
            List<String> unmapped,
            List<ImageRaw> raws,
            CaseImageRef primary,
            String gradcamKey) {
        static VisionBundle abandoned() {
            return new VisionBundle(
                    BranchOutcome.ABANDONED,
                    ErrorCodes.ERR_VISION_BRANCH_TIMEOUT,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    null);
        }

        static VisionBundle failed(String errorCode) {
            return new VisionBundle(BranchOutcome.FAILED, errorCode, null, null, List.of(), List.of(), List.of(), null, null);
        }
    }

    record SpeechBundle(
            BranchOutcome outcome,
            String errorCode,
            String asrModelId,
            String embedModelId,
            String transcriptBn,
            BigDecimal asrConfidence,
            SymptomMatchResult match,
            UUID audioId) {
        static SpeechBundle empty() {
            return new SpeechBundle(BranchOutcome.COMPLETED, null, null, null, null, null, null, null);
        }

        static SpeechBundle abandoned(String errorCode) {
            return new SpeechBundle(BranchOutcome.ABANDONED, errorCode, null, null, null, null, null, null);
        }

        static SpeechBundle failed(String errorCode) {
            return new SpeechBundle(BranchOutcome.FAILED, errorCode, null, null, null, null, null, null);
        }
    }

    public record Assembled(
            AnalysisRun run,
            List<CaseCandidate> candidates,
            List<CaseSymptom> speechSymptoms,
            AnalysisCompleted completed,
            String transcriptBn,
            BigDecimal asrConfidence) {}
}

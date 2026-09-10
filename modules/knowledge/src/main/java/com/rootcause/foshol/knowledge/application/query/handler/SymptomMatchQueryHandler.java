package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.knowledge.api.MatchedSymptom;
import com.rootcause.foshol.knowledge.api.ScoredDisease;
import com.rootcause.foshol.knowledge.api.SymptomMatchRequest;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import com.rootcause.foshol.knowledge.application.config.MatchSettings;
import com.rootcause.foshol.knowledge.application.port.CropIdIndex;
import com.rootcause.foshol.knowledge.application.port.DiseaseScoringPort;
import com.rootcause.foshol.knowledge.application.port.KnnHit;
import com.rootcause.foshol.knowledge.application.port.PhraseIndex;
import com.rootcause.foshol.knowledge.application.port.SymptomCatalog;
import com.rootcause.foshol.knowledge.application.port.SymptomPhraseVectorPort;
import com.rootcause.foshol.knowledge.application.query.SymptomMatchQuery;
import com.rootcause.foshol.knowledge.domain.DiseaseScore;
import com.rootcause.foshol.knowledge.domain.spec.FuzzyHitSpec;
import com.rootcause.foshol.knowledge.domain.FuzzyOverlap;
import com.rootcause.foshol.knowledge.domain.spec.InconclusiveSpec;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import com.rootcause.foshol.knowledge.domain.KnowledgeTaxonomy;
import com.rootcause.foshol.knowledge.domain.MatchLayer;
import com.rootcause.foshol.knowledge.domain.NormalisedText;
import com.rootcause.foshol.knowledge.domain.PhraseIndexEntry;
import com.rootcause.foshol.knowledge.domain.SymptomDeduplicator;
import com.rootcause.foshol.knowledge.domain.SymptomMatch;
import com.rootcause.foshol.knowledge.domain.SymptomRef;
import com.rootcause.foshol.knowledge.domain.spec.VectorHitSpec;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SymptomMatchQueryHandler implements QueryHandler<SymptomMatchQuery, SymptomMatchResult> {

    @Override
    public Class<SymptomMatchQuery> queryType() {
        return SymptomMatchQuery.class;
    }

    private static final Logger log = LoggerFactory.getLogger(SymptomMatchQueryHandler.class);

    private final CropIdIndex cropIds;
    private final PhraseIndex phrases;
    private final SymptomCatalog symptoms;
    private final SymptomPhraseVectorPort vectors;
    private final DiseaseScoringPort scoring;
    private final MatchSettings settings;

    public SymptomMatchQueryHandler(
            CropIdIndex cropIds,
            PhraseIndex phrases,
            SymptomCatalog symptoms,
            SymptomPhraseVectorPort vectors,
            DiseaseScoringPort scoring,
            MatchSettings settings) {
        this.cropIds = cropIds;
        this.phrases = phrases;
        this.symptoms = symptoms;
        this.vectors = vectors;
        this.scoring = scoring;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    @Override
    public SymptomMatchResult handle(SymptomMatchQuery query) {
        SymptomMatchRequest request = query.request();
        if (request.cropId() == null || !cropIds.existsLive(request.cropId())) {
            throw new KnowledgeException(ErrorCodes.ERR_CROP_NOT_FOUND, 404, "Crop not found.");
        }
        float[] embedding = request.transcriptEmbedding();
        if (embedding != null && embedding.length != KnowledgeTaxonomy.EMBEDDING_DIMENSIONS) {
            throw new KnowledgeException(
                    ErrorCodes.ERR_EMBEDDING_DIMENSION, 400, "Embedding dimension is invalid.");
        }

        List<SymptomMatch> automaticHits = new ArrayList<>();
        if (embedding != null) {
            VectorHitSpec vectorSpec = new VectorHitSpec(settings.vectorThreshold());
            for (KnnHit hit : vectors.findNearest(embedding, settings.knnLimit())) {
                if (!vectorSpec.isSatisfiedBy(hit.similarity())) {
                    continue;
                }
                symptoms.findLive(hit.symptomId())
                        .ifPresent(ref -> automaticHits.add(SymptomDeduplicator.vectorHit(
                                ref.id(), ref.code(), ref.nameBn(), hit.similarity())));
            }
        }

        FuzzyHitSpec fuzzySpec = new FuzzyHitSpec(settings.fuzzyThreshold());
        NormalisedText transcript = NormalisedText.of(request.transcriptBn());
        for (PhraseIndexEntry phrase : phrases.entries()) {
            NormalisedText phraseText = NormalisedText.of(phrase.normalisedBn());
            BigDecimal overlap = FuzzyOverlap.overlap(phraseText.tokens(), transcript.tokens());
            if (!fuzzySpec.isSatisfiedBy(overlap)) {
                continue;
            }
            symptoms.findLive(phrase.symptomId())
                    .ifPresent(ref -> automaticHits.add(SymptomDeduplicator.fuzzyHit(
                            ref.id(), ref.code(), ref.nameBn(), overlap)));
        }

        List<SymptomMatch> automatic =
                SymptomDeduplicator.deduplicateAutomatic(automaticHits, settings.maxSymptoms());
        List<SymptomMatch> officerMatches = admitOfficer(request.officerSymptomIds());
        List<SymptomMatch> merged = SymptomDeduplicator.admitOfficer(automatic, officerMatches);

        if (InconclusiveSpec.noSymptomsMatched(merged)) {
            logInconclusive(request.cropId(), 0);
            return new SymptomMatchResult(List.of(), List.of(), true);
        }

        List<DiseaseScore> scored = scoring.score(request.cropId(), merged);
        boolean inconclusive =
                InconclusiveSpec.isInconclusive(merged, scored, settings.inconclusiveScoreMin());
        if (inconclusive) {
            logInconclusive(request.cropId(), merged.size());
        }
        return new SymptomMatchResult(toMatchedSymptoms(merged), toScoredDiseases(scored), inconclusive);
    }

    private List<SymptomMatch> admitOfficer(List<UUID> officerSymptomIds) {
        if (officerSymptomIds == null || officerSymptomIds.isEmpty()) {
            return List.of();
        }
        List<SymptomMatch> admitted = new ArrayList<>();
        for (UUID symptomId : officerSymptomIds) {
            if (symptomId == null) {
                continue;
            }
            var live = symptoms.findLive(symptomId);
            if (live.isEmpty()) {
                log.warn(
                        "ignoring unknown officer symptom id={} correlationId={}",
                        symptomId,
                        CorrelationId.current());
                continue;
            }
            SymptomRef ref = live.get();
            admitted.add(new SymptomMatch(ref.id(), ref.code(), ref.nameBn(), BigDecimal.ONE, MatchLayer.MANUAL));
        }
        return admitted;
    }

    private static void logInconclusive(UUID cropId, int matchedSymptoms) {
        log.warn(
                "inconclusive match cropId={} matchedSymptoms={} correlationId={}",
                cropId,
                matchedSymptoms,
                CorrelationId.current());
    }

    private static List<MatchedSymptom> toMatchedSymptoms(List<SymptomMatch> matches) {
        List<MatchedSymptom> mapped = new ArrayList<>(matches.size());
        for (SymptomMatch match : matches) {
            mapped.add(new MatchedSymptom(
                    match.symptomId(), match.code(), match.nameBn(), match.score(), match.matcher().name()));
        }
        return List.copyOf(mapped);
    }

    private static List<ScoredDisease> toScoredDiseases(List<DiseaseScore> scores) {
        List<ScoredDisease> mapped = new ArrayList<>(scores.size());
        for (DiseaseScore score : scores) {
            mapped.add(new ScoredDisease(
                    score.diseaseId(), score.code(), score.nameBn(), score.score(), score.rank()));
        }
        return List.copyOf(mapped);
    }
}

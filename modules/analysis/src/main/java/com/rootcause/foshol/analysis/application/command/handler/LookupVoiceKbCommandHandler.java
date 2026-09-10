package com.rootcause.foshol.analysis.application.command.handler;

import com.rootcause.foshol.analysis.application.command.LookupVoiceKbCommand;
import com.rootcause.foshol.analysis.application.command.VoiceKbDiseaseCandidate;
import com.rootcause.foshol.analysis.application.command.VoiceKbLookupResult;
import com.rootcause.foshol.analysis.application.port.EmbeddingRequest;
import com.rootcause.foshol.analysis.application.port.EmbeddingResult;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TextEmbeddingPort;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.domain.AnalysisException;
import com.rootcause.foshol.analysis.domain.AnalysisScale;
import com.rootcause.foshol.analysis.domain.DiseaseNameHit;
import com.rootcause.foshol.analysis.domain.DiseaseNameMatcher;
import com.rootcause.foshol.analysis.domain.DiseaseNameRef;
import com.rootcause.foshol.analysis.domain.VoiceKbCandidate;
import com.rootcause.foshol.analysis.domain.VoiceKbCandidateMerger;
import com.rootcause.foshol.analysis.domain.VoiceKbMatchers;
import com.rootcause.foshol.common.util.BanglaNormalizer;
import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.knowledge.api.DiseaseView;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.MatchedSymptom;
import com.rootcause.foshol.knowledge.api.ScoredDisease;
import com.rootcause.foshol.knowledge.api.SymptomMatchApi;
import com.rootcause.foshol.knowledge.api.SymptomMatchRequest;
import com.rootcause.foshol.knowledge.api.SymptomMatchResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LookupVoiceKbCommandHandler implements CommandHandler<LookupVoiceKbCommand, VoiceKbLookupResult> {

    private final KnowledgeQueryApi knowledge;
    private final SymptomMatchApi symptomMatch;
    private final SpeechToTextPort speech;
    private final TextEmbeddingPort embedding;

    public LookupVoiceKbCommandHandler(
            KnowledgeQueryApi knowledge,
            SymptomMatchApi symptomMatch,
            SpeechToTextPort speech,
            TextEmbeddingPort embedding) {
        this.knowledge = knowledge;
        this.symptomMatch = symptomMatch;
        this.speech = speech;
        this.embedding = embedding;
    }

    @Override
    public Class<LookupVoiceKbCommand> commandType() {
        return LookupVoiceKbCommand.class;
    }

    @Override
    public VoiceKbLookupResult handle(LookupVoiceKbCommand command) {
        String correlationId = command.correlationId() == null || command.correlationId().isBlank()
                ? CorrelationId.currentOrCreate()
                : command.correlationId();
        CorrelationId.set(correlationId);
        try {
            if (command.cropId() == null || knowledge.findCropById(command.cropId()).isEmpty()) {
                throw new AnalysisException(ErrorCodes.ERR_CROP_NOT_FOUND, 404, "Crop was not found.");
            }
            if (command.audio() == null || command.audio().length == 0) {
                throw new AnalysisException(ErrorCodes.ERR_AUDIO_UNREADABLE, 422, "Audio was empty or unreadable.");
            }
            String language = language(command.preferredLanguage());
            TranscriptResult transcript;
            try {
                transcript = speech.transcribeAudio(command.audio(), language, correlationId);
            } catch (SidecarFailureException ex) {
                throw sidecar(ex);
            }
            String raw = transcript.transcriptBn() == null ? "" : transcript.transcriptBn().trim();
            String normalised = BanglaNormalizer.forStorage(raw);
            BigDecimal asrConfidence = transcript.asrConfidence() == null
                    ? BigDecimal.ZERO
                    : AnalysisScale.confidence(transcript.asrConfidence());
            if (normalised == null || normalised.isBlank()) {
                return new VoiceKbLookupResult("", asrConfidence, List.of(), true);
            }
            List<DiseaseNameRef> catalogue = catalogue(command.cropId());
            List<DiseaseNameHit> nameHits = DiseaseNameMatcher.match(normalised, catalogue);
            List<VoiceKbCandidate> symptomHits = symptomHits(command.cropId(), normalised, correlationId, catalogue);
            List<VoiceKbCandidate> merged =
                    VoiceKbCandidateMerger.merge(nameHits, symptomHits, VoiceKbMatchers.CANDIDATE_LIMIT);
            boolean inconclusive = merged.isEmpty();
            return new VoiceKbLookupResult(normalised, asrConfidence, toView(merged), inconclusive);
        } finally {
            CorrelationId.clear();
        }
    }

    private List<VoiceKbCandidate> symptomHits(
            UUID cropId, String normalised, String correlationId, List<DiseaseNameRef> catalogue) {
        EmbeddingResult embedded;
        try {
            embedded = embedding.embed(new EmbeddingRequest(Uuid7.create(), normalised, correlationId));
        } catch (SidecarFailureException ex) {
            throw sidecar(ex);
        }
        float[] vector = embedded.vector();
        if (vector == null || vector.length == 0 || isAllZeros(vector)) {
            return List.of();
        }
        if (vector.length != AnalysisScale.EMBEDDING_DIMENSION) {
            throw new AnalysisException(
                    ErrorCodes.ERR_EMBEDDING_DIMENSION, 422, "Embedding dimension was not 768.");
        }
        SymptomMatchResult match = symptomMatch.match(new SymptomMatchRequest(cropId, normalised, vector, List.of()));
        if (match == null || match.diseases() == null || match.diseases().isEmpty()) {
            return List.of();
        }
        String matcher = symptomMatcher(match.symptoms());
        List<VoiceKbCandidate> hits = new ArrayList<>();
        for (ScoredDisease disease : match.diseases()) {
            if (disease == null) {
                continue;
            }
            DiseaseNameRef catalogueHit = catalogueName(catalogue, disease.diseaseId());
            String nameEn = catalogueHit == null ? null : catalogueHit.nameEn();
            String nameBn = disease.nameBn();
            if (catalogueHit != null && catalogueHit.nameBn() != null && !catalogueHit.nameBn().isBlank()) {
                nameBn = catalogueHit.nameBn();
            }
            hits.add(new VoiceKbCandidate(
                    disease.diseaseId(),
                    disease.code(),
                    nameBn,
                    nameEn,
                    AnalysisScale.score(disease.score()),
                    matcher));
        }
        return hits;
    }

    private static DiseaseNameRef catalogueName(List<DiseaseNameRef> catalogue, UUID diseaseId) {
        if (catalogue == null || diseaseId == null) {
            return null;
        }
        for (DiseaseNameRef ref : catalogue) {
            if (ref.id().equals(diseaseId)) {
                return ref;
            }
        }
        return null;
    }

    private List<DiseaseNameRef> catalogue(UUID cropId) {
        List<DiseaseView> diseases = knowledge.listDiseasesByCrop(cropId);
        List<DiseaseNameRef> refs = new ArrayList<>();
        for (DiseaseView disease : diseases) {
            refs.add(new DiseaseNameRef(
                    disease.id(), disease.code(), disease.nameBn(), disease.nameEn(), disease.healthy()));
        }
        return refs;
    }

    private static List<VoiceKbDiseaseCandidate> toView(List<VoiceKbCandidate> merged) {
        List<VoiceKbDiseaseCandidate> views = new ArrayList<>(merged.size());
        for (VoiceKbCandidate hit : merged) {
            views.add(new VoiceKbDiseaseCandidate(
                    hit.diseaseId(), hit.code(), hit.nameBn(), hit.nameEn(), hit.score(), hit.matcher()));
        }
        return List.copyOf(views);
    }

    private static String symptomMatcher(List<MatchedSymptom> symptoms) {
        if (symptoms == null) {
            return VoiceKbMatchers.FUZZY;
        }
        for (MatchedSymptom symptom : symptoms) {
            if (symptom != null && VoiceKbMatchers.VECTOR.equals(symptom.matcher())) {
                return VoiceKbMatchers.VECTOR;
            }
        }
        return VoiceKbMatchers.FUZZY;
    }

    private static String language(String preferred) {
        if (preferred == null || preferred.isBlank()) {
            return VoiceKbMatchers.LANGUAGE_BN;
        }
        return preferred.trim();
    }

    private static boolean isAllZeros(float[] vector) {
        for (float value : vector) {
            if (value != 0.0f) {
                return false;
            }
        }
        return true;
    }

    private static AnalysisException sidecar(SidecarFailureException ex) {
        int status = 503;
        if (ErrorCodes.ERR_SIDECAR_PAYLOAD_TOO_LARGE.equals(ex.errorCode())) {
            status = 413;
        } else if (ErrorCodes.ERR_SIDECAR_UNSUPPORTED_MEDIA.equals(ex.errorCode())) {
            status = 415;
        } else if (ErrorCodes.ERR_SIDECAR_UNDECODABLE.equals(ex.errorCode())
                || ErrorCodes.ERR_SIDECAR_BAD_REQUEST.equals(ex.errorCode())) {
            status = 422;
        }
        return new AnalysisException(ex.errorCode(), status, ex.getMessage());
    }
}

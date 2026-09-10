package com.rootcause.foshol.knowledge.infrastructure.adapter;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.knowledge.application.port.KnowledgeContentPort;
import com.rootcause.foshol.knowledge.application.port.LabelKey;
import com.rootcause.foshol.knowledge.domain.PhraseIndexEntry;
import com.rootcause.foshol.knowledge.domain.SymptomRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.rootcause.foshol.knowledge.infrastructure.persistence.CropJpaRepository;
import com.rootcause.foshol.knowledge.infrastructure.persistence.CropReadRow;
import com.rootcause.foshol.knowledge.infrastructure.persistence.DiseaseJpaRepository;
import com.rootcause.foshol.knowledge.infrastructure.persistence.DiseaseSymptomJpaRepository;
import com.rootcause.foshol.knowledge.infrastructure.persistence.ModelLabelMapEntity;
import com.rootcause.foshol.knowledge.infrastructure.persistence.ModelLabelMapJpaRepository;
import com.rootcause.foshol.knowledge.infrastructure.persistence.RemedyJpaRepository;
import com.rootcause.foshol.knowledge.infrastructure.persistence.SymptomJpaRepository;
import com.rootcause.foshol.knowledge.infrastructure.persistence.SymptomPhraseEntity;
import com.rootcause.foshol.knowledge.infrastructure.persistence.SymptomPhraseJpaRepository;

@Component
public class KnowledgeContentAdapter implements KnowledgeContentPort {

    private final CropJpaRepository crops;
    private final DiseaseJpaRepository diseases;
    private final SymptomJpaRepository symptoms;
    private final SymptomPhraseJpaRepository phrases;
    private final RemedyJpaRepository remedies;
    private final DiseaseSymptomJpaRepository weights;
    private final ModelLabelMapJpaRepository labels;
    private final List<String> configuredVisionModelIds;

    public KnowledgeContentAdapter(
            CropJpaRepository crops,
            DiseaseJpaRepository diseases,
            SymptomJpaRepository symptoms,
            SymptomPhraseJpaRepository phrases,
            RemedyJpaRepository remedies,
            DiseaseSymptomJpaRepository weights,
            ModelLabelMapJpaRepository labels,
            @Value("${" + ConfigKeys.AI_VISION_RICE_MODEL_ID + "}") String riceModelId,
            @Value("${" + ConfigKeys.AI_VISION_RICE_FALLBACK_MODEL_ID + "}") String riceFallbackModelId,
            @Value("${" + ConfigKeys.AI_VISION_SOLANACEAE_MODEL_ID + "}") String solanaceaeModelId) {
        this.crops = crops;
        this.diseases = diseases;
        this.symptoms = symptoms;
        this.phrases = phrases;
        this.remedies = remedies;
        this.weights = weights;
        this.labels = labels;
        this.configuredVisionModelIds = List.of(riceModelId, riceFallbackModelId, solanaceaeModelId);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentSnapshot snapshot() {
        Set<String> modelsWithRows = new HashSet<>(labels.findDistinctModelIds());
        List<String> missingModels = new ArrayList<>();
        for (String modelId : configuredVisionModelIds) {
            if (!modelsWithRows.contains(modelId)) {
                missingModels.add(modelId);
            }
        }
        return new ContentSnapshot(
                Math.toIntExact(diseases.countLive()),
                diseases.findNonHealthyMissingActiveRemedy(),
                diseases.findNonHealthyMissingWeights(),
                remedies.findLiveWithBlankSourceRef(),
                phrases.findLiveMissingEmbedding(),
                labels.findUnresolvedDiseaseIds(),
                missingModels,
                phrases.findLivePhrases().size(),
                Math.toIntExact(remedies.countLive()),
                Math.toIntExact(weights.count()),
                Math.toIntExact(labels.count()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhraseIndexEntry> loadLivePhrases() {
        List<PhraseIndexEntry> entries = new ArrayList<>();
        for (SymptomPhraseEntity phrase : phrases.findLivePhrases()) {
            entries.add(new PhraseIndexEntry(phrase.getId(), phrase.getSymptomId(), phrase.getNormalisedBn()));
        }
        return List.copyOf(entries);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SymptomRef> loadLiveSymptoms() {
        return symptoms.findLiveSymptoms().stream()
                .map(row -> new SymptomRef(row.getId(), row.getCode(), row.getNameBn()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<LabelKey, UUID> loadLabelMap() {
        Map<LabelKey, UUID> map = new LinkedHashMap<>();
        for (ModelLabelMapEntity row : labels.findAll()) {
            map.put(new LabelKey(row.getModelId(), row.getModelVersion(), row.getRawLabel()), row.getDiseaseId());
        }
        return Map.copyOf(map);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> loadLiveCropIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (CropReadRow crop : crops.findLiveCrops()) {
            ids.add(crop.getId());
        }
        return Set.copyOf(ids);
    }
}

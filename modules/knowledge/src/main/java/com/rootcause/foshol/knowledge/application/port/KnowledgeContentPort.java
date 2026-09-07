package com.rootcause.foshol.knowledge.application.port;

import com.rootcause.foshol.knowledge.domain.PhraseIndexEntry;
import com.rootcause.foshol.knowledge.domain.SymptomRef;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface KnowledgeContentPort {

    ContentSnapshot snapshot();

    List<PhraseIndexEntry> loadLivePhrases();

    List<SymptomRef> loadLiveSymptoms();

    Map<LabelKey, UUID> loadLabelMap();

    Set<UUID> loadLiveCropIds();

    record ContentSnapshot(
            int liveDiseaseCount,
            List<UUID> nonHealthyMissingActiveRemedy,
            List<UUID> nonHealthyMissingWeights,
            List<UUID> liveRemediesWithBlankSourceRef,
            List<UUID> livePhrasesMissingEmbedding,
            List<UUID> labelMapDiseaseIdsMissing,
            List<String> configuredModelIdsWithNoRow,
            int livePhraseCount,
            int liveRemedyCount,
            int diseaseSymptomRowCount,
            int labelMapRowCount) {}
}

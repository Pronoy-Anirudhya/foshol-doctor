package com.rootcause.foshol.knowledge.infrastructure.persistence;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.knowledge.application.port.KnowledgeContentPort.ContentSnapshot;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import com.rootcause.foshol.knowledge.domain.KnowledgeTaxonomy;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KnowledgeContentValidator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeContentValidator.class);

    private KnowledgeContentValidator() {}

    public static void validateStrict(ContentSnapshot snapshot) {
        if (snapshot.liveDiseaseCount() != KnowledgeTaxonomy.DISEASE_CLASS_COUNT) {
            fail(
                    ErrorCodes.ERR_KB_CONTENT_INVALID,
                    "KNOWLEDGE-DATA-004",
                    List.of(),
                    "live disease count is " + snapshot.liveDiseaseCount());
        }
        if (!snapshot.nonHealthyMissingActiveRemedy().isEmpty()) {
            fail(
                    ErrorCodes.ERR_KB_CONTENT_INVALID,
                    "KNOWLEDGE-DATA-007",
                    snapshot.nonHealthyMissingActiveRemedy(),
                    "non-healthy disease missing an active remedy");
        }
        if (!snapshot.nonHealthyMissingWeights().isEmpty()) {
            fail(
                    ErrorCodes.ERR_KB_CONTENT_INVALID,
                    "KNOWLEDGE-DATA-008",
                    snapshot.nonHealthyMissingWeights(),
                    "non-healthy disease missing disease_symptom weights");
        }
        if (!snapshot.liveRemediesWithBlankSourceRef().isEmpty()) {
            fail(
                    ErrorCodes.ERR_KB_CONTENT_INVALID,
                    "KNOWLEDGE-DATA-009",
                    snapshot.liveRemediesWithBlankSourceRef(),
                    "remedy source_ref is blank");
        }
        if (!snapshot.livePhrasesMissingEmbedding().isEmpty()) {
            fail(
                    ErrorCodes.ERR_KB_EMBEDDING_MISSING,
                    "KNOWLEDGE-DATA-011",
                    snapshot.livePhrasesMissingEmbedding(),
                    "symptom_phrase embedding is null");
        }
        if (!snapshot.configuredModelIdsWithNoRow().isEmpty()) {
            fail(
                    ErrorCodes.ERR_MODEL_LABEL_MAP_MISSING,
                    "KNOWLEDGE-DATA-013",
                    List.of(),
                    "configured vision model has no model_label_map row");
        }
        if (!snapshot.labelMapDiseaseIdsMissing().isEmpty()) {
            fail(
                    ErrorCodes.ERR_MODEL_LABEL_MAP_INVALID,
                    "KNOWLEDGE-DATA-014",
                    snapshot.labelMapDiseaseIdsMissing(),
                    "model_label_map.disease_id does not resolve");
        }
    }

    private static void fail(String errorCode, String requirementId, List<UUID> rowIds, String detail) {
        log.error(
                "knowledge content validation failed requirementId={} errorCode={} rowIds={}",
                requirementId,
                errorCode,
                rowIds);
        throw new KnowledgeException(errorCode, 500, requirementId + ": " + detail);
    }
}

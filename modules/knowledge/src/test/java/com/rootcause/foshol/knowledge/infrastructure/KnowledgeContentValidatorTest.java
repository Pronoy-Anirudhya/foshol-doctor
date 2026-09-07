package com.rootcause.foshol.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.application.port.KnowledgeContentPort.ContentSnapshot;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeContentValidatorTest {

    @Test
    void wrongDiseaseCountFails() {
        assertCode(empty().diseases(13), ErrorCodes.ERR_KB_CONTENT_INVALID);
    }

    @Test
    void missingRemedyFails() {
        assertCode(populated().missingRemedy(List.of(UUID.randomUUID())), ErrorCodes.ERR_KB_CONTENT_INVALID);
    }

    @Test
    void missingWeightsFail() {
        assertCode(populated().missingWeights(List.of(UUID.randomUUID())), ErrorCodes.ERR_KB_CONTENT_INVALID);
    }

    @Test
    void blankSourceRefFails() {
        assertCode(populated().blankSource(List.of(UUID.randomUUID())), ErrorCodes.ERR_KB_CONTENT_INVALID);
    }

    @Test
    void missingEmbeddingFails() {
        assertCode(populated().missingEmbedding(List.of(UUID.randomUUID())), ErrorCodes.ERR_KB_EMBEDDING_MISSING);
    }

    @Test
    void missingLabelMapFails() {
        assertCode(populated().missingModels(List.of("model-a")), ErrorCodes.ERR_MODEL_LABEL_MAP_MISSING);
    }

    @Test
    void invalidLabelDiseaseFails() {
        assertCode(
                populated().invalidLabelDiseases(List.of(UUID.randomUUID())),
                ErrorCodes.ERR_MODEL_LABEL_MAP_INVALID);
    }

    @Test
    void populatedSnapshotPasses() {
        KnowledgeContentValidator.validateStrict(populated().build());
    }

    private static void assertCode(Fixture fixture, String code) {
        assertThatThrownBy(() -> KnowledgeContentValidator.validateStrict(fixture.build()))
                .isInstanceOf(KnowledgeException.class)
                .extracting(ex -> ((KnowledgeException) ex).errorCode())
                .isEqualTo(code);
    }

    private static Fixture empty() {
        return new Fixture(14, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0);
    }

    private static Fixture populated() {
        return new Fixture(14, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 1, 1, 1, 1);
    }

    private record Fixture(
            int diseases,
            List<UUID> missingRemedy,
            List<UUID> missingWeights,
            List<UUID> blankSource,
            List<UUID> missingEmbedding,
            List<UUID> invalidLabels,
            List<String> missingModels,
            int phrases,
            int remedies,
            int weights,
            int labels) {

        Fixture diseases(int count) {
            return new Fixture(
                    count,
                    missingRemedy,
                    missingWeights,
                    blankSource,
                    missingEmbedding,
                    invalidLabels,
                    missingModels,
                    phrases,
                    remedies,
                    weights,
                    labels);
        }

        Fixture missingRemedy(List<UUID> ids) {
            return new Fixture(
                    diseases,
                    ids,
                    missingWeights,
                    blankSource,
                    missingEmbedding,
                    invalidLabels,
                    missingModels,
                    phrases,
                    remedies,
                    weights,
                    labels);
        }

        Fixture missingWeights(List<UUID> ids) {
            return new Fixture(
                    diseases,
                    missingRemedy,
                    ids,
                    blankSource,
                    missingEmbedding,
                    invalidLabels,
                    missingModels,
                    phrases,
                    remedies,
                    weights,
                    labels);
        }

        Fixture blankSource(List<UUID> ids) {
            return new Fixture(
                    diseases,
                    missingRemedy,
                    missingWeights,
                    ids,
                    missingEmbedding,
                    invalidLabels,
                    missingModels,
                    phrases,
                    remedies,
                    weights,
                    labels);
        }

        Fixture missingEmbedding(List<UUID> ids) {
            return new Fixture(
                    diseases,
                    missingRemedy,
                    missingWeights,
                    blankSource,
                    ids,
                    invalidLabels,
                    missingModels,
                    phrases,
                    remedies,
                    weights,
                    labels);
        }

        Fixture missingModels(List<String> models) {
            return new Fixture(
                    diseases,
                    missingRemedy,
                    missingWeights,
                    blankSource,
                    missingEmbedding,
                    invalidLabels,
                    models,
                    phrases,
                    remedies,
                    weights,
                    labels);
        }

        Fixture invalidLabelDiseases(List<UUID> ids) {
            return new Fixture(
                    diseases,
                    missingRemedy,
                    missingWeights,
                    blankSource,
                    missingEmbedding,
                    ids,
                    missingModels,
                    phrases,
                    remedies,
                    weights,
                    labels);
        }

        ContentSnapshot build() {
            return new ContentSnapshot(
                    diseases,
                    missingRemedy,
                    missingWeights,
                    blankSource,
                    missingEmbedding,
                    invalidLabels,
                    missingModels,
                    phrases,
                    remedies,
                    weights,
                    labels);
        }
    }
}

package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.EmbeddingRequest;
import com.rootcause.foshol.analysis.application.port.EmbeddingResult;
import com.rootcause.foshol.analysis.application.port.TextEmbeddingPort;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ConfigKeys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = ConfigKeys.AI_MODE, havingValue = "replay")
public class FixtureTextEmbeddingAdapter implements TextEmbeddingPort {

    private final ObjectMapper mapper;
    private final SidecarCallSupport sidecar;

    public FixtureTextEmbeddingAdapter(ObjectMapper mapper, SidecarCallSupport sidecar) {
        this.mapper = mapper;
        this.sidecar = sidecar;
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        return sidecar.execute("embed", () -> {
            String sha = sha256Hex(request.normalisedText());
            JsonNode node = FixtureLoader.json(mapper, "embed", sha);
            JsonNode vectorNode = node.get("vector");
            if (vectorNode == null || !vectorNode.isArray()) {
                return new EmbeddingResult("fixture-embed", new float[0], 0);
            }
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }
            return new EmbeddingResult(
                    node.path("modelId").asText("fixture-embed"), vector, node.path("latencyMs").asInt(0));
        });
    }

    static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

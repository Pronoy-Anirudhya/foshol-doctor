package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rootcause.foshol.analysis.application.port.EmbeddingRequest;
import com.rootcause.foshol.analysis.application.port.EmbeddingResult;
import com.rootcause.foshol.analysis.application.port.TextEmbeddingPort;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "foshol.ai", name = "mode", havingValue = "live")
public class HttpTextEmbeddingAdapter implements TextEmbeddingPort {

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;

    public HttpTextEmbeddingAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        return sidecar.execute("embed", () -> {
            ObjectNode body = http.object();
            body.putArray("texts").add(request.normalisedText());
            JsonNode node = http.postJson("/v1/embed", body, request.correlationId());
            return new EmbeddingResult(
                    text(node, "model_id", "modelId"), firstEmbedding(node), latencyMs(node));
        });
    }

    private static float[] firstEmbedding(JsonNode node) {
        JsonNode embeddings = node.get("embeddings");
        if (embeddings != null && embeddings.isArray() && embeddings.size() > 0) {
            JsonNode first = embeddings.get(0);
            if (first != null && first.isArray()) {
                return toFloats(first);
            }
        }
        JsonNode vector = node.get("vector");
        if (vector != null && vector.isArray()) {
            return toFloats(vector);
        }
        return new float[0];
    }

    private static float[] toFloats(JsonNode array) {
        float[] vector = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            vector[i] = (float) array.get(i).asDouble();
        }
        return vector;
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull()) {
                return node.get(name).asText();
            }
        }
        return "";
    }

    private static int latencyMs(JsonNode node) {
        if (node.has("inference_ms")) {
            return node.get("inference_ms").asInt();
        }
        if (node.has("inferenceMs")) {
            return node.get("inferenceMs").asInt();
        }
        if (node.has("latency_ms")) {
            return node.get("latency_ms").asInt();
        }
        if (node.has("latencyMs")) {
            return node.get("latencyMs").asInt();
        }
        return 0;
    }
}

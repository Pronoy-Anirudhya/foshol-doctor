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
            body.put("text", request.normalisedText());
            JsonNode node = http.postJson("/v1/embed", body, request.correlationId());
            JsonNode vectorNode = node.get("vector");
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }
            String model = node.has("model_id") ? node.get("model_id").asText() : node.path("modelId").asText();
            return new EmbeddingResult(model, vector, node.path("latency_ms").asInt(node.path("latencyMs").asInt()));
        });
    }
}

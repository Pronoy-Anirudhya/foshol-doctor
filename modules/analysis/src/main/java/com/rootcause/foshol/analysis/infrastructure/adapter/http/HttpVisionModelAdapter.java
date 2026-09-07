package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rootcause.foshol.analysis.application.port.RawCandidate;
import com.rootcause.foshol.analysis.application.port.VisionModelPort;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "foshol.ai", name = "mode", havingValue = "live")
public class HttpVisionModelAdapter implements VisionModelPort {

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;

    public HttpVisionModelAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public VisionResult classify(VisionRequest request) {
        return sidecar.execute("vision", () -> {
            ObjectNode body = http.object();
            body.put("crop_code", request.cropCode());
            body.put("sha256", request.sha256());
            body.put("image_base64", http.readBase64(request.objectKey()));
            JsonNode node = http.postJson("/v1/vision/classify", body, request.correlationId());
            List<RawCandidate> candidates = new ArrayList<>();
            for (JsonNode c : node.withArray("candidates")) {
                String label = c.has("raw_label") ? c.get("raw_label").asText() : c.get("rawLabel").asText();
                candidates.add(new RawCandidate(label, new BigDecimal(c.get("confidence").asText())));
            }
            return new VisionResult(
                    text(node, "model_id", "modelId"),
                    text(node, "model_version", "modelVersion"),
                    candidates,
                    node.path("latency_ms").asInt(node.path("latencyMs").asInt()));
        });
    }

    private static String text(JsonNode node, String snake, String camel) {
        if (node.has(snake)) {
            return node.get(snake).asText();
        }
        return node.path(camel).asText();
    }
}

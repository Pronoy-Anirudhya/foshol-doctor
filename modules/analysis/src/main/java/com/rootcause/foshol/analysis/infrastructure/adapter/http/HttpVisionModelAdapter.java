package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.rootcause.foshol.analysis.application.port.RawCandidate;
import com.rootcause.foshol.analysis.application.port.VisionModelPort;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "foshol.ai", name = "mode", havingValue = "live")
public class HttpVisionModelAdapter implements VisionModelPort {

    private static final int CACHE_MAX = 64;

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;
    private final Map<String, VisionResult> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, VisionResult> eldest) {
            return size() > CACHE_MAX;
        }
    });

    public HttpVisionModelAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public VisionResult classify(VisionRequest request) {
        String cacheKey = request.sha256() + "|" + request.cropCode();
        VisionResult cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        VisionResult result = sidecar.execute("vision", () -> {
            byte[] image = http.readBytes(request.objectKey());
            JsonNode node = http.postMultipart(
                    "/v1/vision/classify", image, "leaf.bin", request.cropCode(), request.correlationId());
            List<RawCandidate> candidates = new ArrayList<>();
            JsonNode rows = node.has("predictions") ? node.get("predictions") : node.withArray("candidates");
            for (JsonNode c : rows) {
                String label = c.has("raw_label") ? c.get("raw_label").asText() : c.get("rawLabel").asText();
                candidates.add(new RawCandidate(label, new BigDecimal(c.get("confidence").asText())));
            }
            int latency = node.path("inference_ms").asInt(node.path("inferenceMs").asInt(node.path("latency_ms")
                    .asInt(node.path("latencyMs").asInt())));
            return new VisionResult(
                    text(node, "model_id", "modelId"),
                    text(node, "model_version", "modelVersion"),
                    candidates,
                    latency);
        });
        cache.put(cacheKey, result);
        return result;
    }

    private static String text(JsonNode node, String snake, String camel) {
        if (node.has(snake)) {
            return node.get(snake).asText();
        }
        return node.path(camel).asText();
    }
}

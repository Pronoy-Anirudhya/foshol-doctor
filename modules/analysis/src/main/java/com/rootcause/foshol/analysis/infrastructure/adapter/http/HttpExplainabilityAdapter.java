package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rootcause.foshol.analysis.application.port.ExplainabilityPort;
import com.rootcause.foshol.analysis.application.port.ExplanationRequest;
import com.rootcause.foshol.analysis.application.port.ExplanationResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "foshol.ai", name = "mode", havingValue = "live")
public class HttpExplainabilityAdapter implements ExplainabilityPort {

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;

    public HttpExplainabilityAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public ExplanationResult explain(ExplanationRequest request) {
        return sidecar.execute("gradcam", () -> {
            ObjectNode body = http.object();
            body.put("crop_code", request.cropCode());
            body.put("sha256", request.sha256());
            body.put("raw_label", request.rawLabel());
            body.put("image_base64", http.readBase64(request.objectKey()));
            JsonNode node = http.postJson("/v1/vision/explain", body, request.correlationId());
            String b64 = node.has("overlay_png_base64")
                    ? node.get("overlay_png_base64").asText()
                    : node.path("overlayPngBase64").asText();
            byte[] png = Base64.getDecoder().decode(b64);
            String model = node.has("model_id") ? node.get("model_id").asText() : node.path("modelId").asText();
            String version = node.has("model_version") ? node.get("model_version").asText() : node.path("modelVersion").asText();
            return new ExplanationResult(model, version, png, "image/png", node.path("latency_ms").asInt());
        });
    }
}

package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TranscriptRequest;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "foshol.ai", name = "mode", havingValue = "live")
public class HttpSpeechToTextAdapter implements SpeechToTextPort {

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;

    public HttpSpeechToTextAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public TranscriptResult transcribe(TranscriptRequest request) {
        return sidecar.execute("asr", () -> {
            ObjectNode body = http.object();
            body.put("sha256", request.sha256());
            body.put("audio_base64", http.readBase64(request.objectKey()));
            JsonNode node = http.postJson("/v1/asr/transcribe", body, request.correlationId());
            String transcript = node.has("transcript_bn") ? node.get("transcript_bn").asText() : node.path("transcriptBn").asText();
            String model = node.has("model_id") ? node.get("model_id").asText() : node.path("modelId").asText();
            BigDecimal conf = new BigDecimal(
                    node.has("asr_confidence") ? node.get("asr_confidence").asText() : node.path("asrConfidence").asText("0"));
            return new TranscriptResult(model, transcript, conf, node.path("latency_ms").asInt(node.path("latencyMs").asInt()));
        });
    }
}

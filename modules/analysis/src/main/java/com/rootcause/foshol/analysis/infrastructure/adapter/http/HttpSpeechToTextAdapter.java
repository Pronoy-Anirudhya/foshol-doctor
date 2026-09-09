package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
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
            byte[] audio = http.readBytes(request.objectKey());
            JsonNode node = http.postMultipartAudio(
                    "/v1/asr/transcribe", audio, "audio.bin", request.correlationId());
            return new TranscriptResult(
                    text(node, "model_id", "modelId"),
                    text(node, "transcript", "transcript_bn", "transcriptBn"),
                    decimal(node, "confidence", "asr_confidence", "asrConfidence"),
                    latencyMs(node));
        });
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull()) {
                return node.get(name).asText();
            }
        }
        return "";
    }

    private static BigDecimal decimal(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull()) {
                return new BigDecimal(node.get(name).asText());
            }
        }
        return BigDecimal.ZERO;
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

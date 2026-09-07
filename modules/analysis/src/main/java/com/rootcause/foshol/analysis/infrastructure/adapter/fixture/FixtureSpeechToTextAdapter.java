package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TranscriptRequest;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ConfigKeys;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = ConfigKeys.AI_MODE, havingValue = "replay")
public class FixtureSpeechToTextAdapter implements SpeechToTextPort {

    private final ObjectMapper mapper;
    private final SidecarCallSupport sidecar;

    public FixtureSpeechToTextAdapter(ObjectMapper mapper, SidecarCallSupport sidecar) {
        this.mapper = mapper;
        this.sidecar = sidecar;
    }

    @Override
    public TranscriptResult transcribe(TranscriptRequest request) {
        return sidecar.execute("asr", () -> {
            JsonNode node = FixtureLoader.json(mapper, "asr", request.sha256());
            return new TranscriptResult(
                    node.get("modelId").asText(),
                    node.get("transcriptBn").asText(),
                    new BigDecimal(node.get("asrConfidence").asText()),
                    node.get("latencyMs").asInt());
        });
    }
}

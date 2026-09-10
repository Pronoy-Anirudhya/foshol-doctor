package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TranscriptRequest;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ConfigKeys;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
        return sidecar.execute("asr", () -> parse(FixtureLoader.json(mapper, "asr", request.sha256())));
    }

    @Override
    public TranscriptResult transcribeAudio(byte[] audio, String language, String correlationId) {
        return sidecar.execute("asr", () -> parse(FixtureLoader.json(mapper, "asr", sha256Hex(audio))));
    }

    private static TranscriptResult parse(JsonNode node) {
        return new TranscriptResult(
                node.get("modelId").asText(),
                node.get("transcriptBn").asText(),
                new BigDecimal(node.get("asrConfidence").asText()),
                node.get("latencyMs").asInt());
    }

    static String sha256Hex(byte[] audio) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(audio == null ? new byte[0] : audio);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.RawCandidate;
import com.rootcause.foshol.analysis.application.port.VisionModelPort;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ConfigKeys;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = ConfigKeys.AI_MODE, havingValue = "replay")
public class FixtureVisionModelAdapter implements VisionModelPort {

    private final ObjectMapper mapper;
    private final SidecarCallSupport sidecar;

    public FixtureVisionModelAdapter(ObjectMapper mapper, SidecarCallSupport sidecar) {
        this.mapper = mapper;
        this.sidecar = sidecar;
    }

    @Override
    public VisionResult classify(VisionRequest request) {
        return sidecar.execute("vision", () -> {
            JsonNode node = FixtureLoader.json(mapper, "vision", request.sha256());
            List<RawCandidate> candidates = new ArrayList<>();
            for (JsonNode c : node.get("candidates")) {
                candidates.add(new RawCandidate(
                        c.get("rawLabel").asText(), new BigDecimal(c.get("confidence").asText())));
            }
            return new VisionResult(
                    node.get("modelId").asText(),
                    node.get("modelVersion").asText(),
                    candidates,
                    node.get("latencyMs").asInt());
        });
    }
}

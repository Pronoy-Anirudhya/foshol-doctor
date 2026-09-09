package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.RawCandidate;
import com.rootcause.foshol.analysis.application.port.VisionBatchRequest;
import com.rootcause.foshol.analysis.application.port.VisionBatchResult;
import com.rootcause.foshol.analysis.application.port.VisionImageRef;
import com.rootcause.foshol.analysis.application.port.VisionImageResult;
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
        return sidecar.execute("vision", () -> loadFixture(request.sha256()));
    }

    @Override
    public VisionBatchResult classifyBatch(VisionBatchRequest request) {
        return sidecar.execute("vision", () -> {
            long started = System.nanoTime();
            String modelId = null;
            String modelVersion = null;
            List<VisionImageResult> images = new ArrayList<>();
            for (VisionImageRef image : request.images()) {
                VisionResult one = loadFixture(image.sha256());
                modelId = one.modelId();
                modelVersion = one.modelVersion();
                images.add(new VisionImageResult(image.imageId(), image.sha256(), one.candidates()));
            }
            int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000L);
            return new VisionBatchResult(modelId, modelVersion, List.copyOf(images), latencyMs);
        });
    }

    private VisionResult loadFixture(String sha256) {
        JsonNode node = FixtureLoader.json(mapper, "vision", sha256);
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
    }
}

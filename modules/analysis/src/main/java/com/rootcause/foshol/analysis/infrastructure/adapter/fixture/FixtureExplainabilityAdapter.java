package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import com.rootcause.foshol.analysis.application.port.ExplainabilityPort;
import com.rootcause.foshol.analysis.application.port.ExplanationRequest;
import com.rootcause.foshol.analysis.application.port.ExplanationResult;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.contract.ConfigKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = ConfigKeys.AI_MODE, havingValue = "replay")
public class FixtureExplainabilityAdapter implements ExplainabilityPort {

    private final SidecarCallSupport sidecar;

    public FixtureExplainabilityAdapter(SidecarCallSupport sidecar) {
        this.sidecar = sidecar;
    }

    @Override
    public ExplanationResult explain(ExplanationRequest request) {
        return sidecar.execute("gradcam", () -> {
            byte[] png = FixtureLoader.bytes("gradcam", request.sha256(), "png");
            return new ExplanationResult("gradcam-fixture", "replay", png, "image/png", 1);
        });
    }
}

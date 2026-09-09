package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.rootcause.foshol.analysis.application.port.ExplainabilityPort;
import com.rootcause.foshol.analysis.application.port.ExplanationRequest;
import com.rootcause.foshol.analysis.application.port.ExplanationResult;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
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
            try {
                byte[] image = http.readBytes(request.objectKey());
                return http.postMultipartPng(
                        "/v1/vision/explain",
                        image,
                        "leaf.png",
                        request.cropCode(),
                        request.rawLabel(),
                        request.correlationId());
            } catch (SidecarFailureException ex) {
                if (ex.expectedExplainFailure()) {
                    return null;
                }
                throw ex;
            }
        });
    }
}

package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rootcause.foshol.analysis.application.AnalysisSettings;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.ErrorCodes;
import java.util.Base64;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class SidecarHttpClient {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final ObjectStorePort objectStore;

    public SidecarHttpClient(AnalysisSettings settings, ObjectMapper mapper, ObjectStorePort objectStore) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(settings.aiTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(settings.aiBaseUrl())
                .requestFactory(factory)
                .build();
        this.mapper = mapper;
        this.objectStore = objectStore;
    }

    public JsonNode postJson(String path, ObjectNode body, String correlationId) {
        try {
            String response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CorrelationId.HEADER, correlationId)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(response);
        } catch (RestClientException | java.io.IOException ex) {
            throw new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "Sidecar HTTP failed", ex);
        }
    }

    public String readBase64(String objectKey) {
        return Base64.getEncoder().encodeToString(objectStore.read(objectKey));
    }

    public ObjectNode object() {
        return mapper.createObjectNode();
    }
}

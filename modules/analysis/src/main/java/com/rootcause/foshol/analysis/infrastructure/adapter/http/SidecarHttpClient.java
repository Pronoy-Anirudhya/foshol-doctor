package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rootcause.foshol.analysis.application.AnalysisSettings;
import com.rootcause.foshol.analysis.application.port.ExplanationResult;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.CorrelationId;
import java.io.IOException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class SidecarHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SidecarHttpClient.class);

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

    SidecarHttpClient(RestClient restClient, ObjectMapper mapper, ObjectStorePort objectStore) {
        this.restClient = restClient;
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
        } catch (RestClientException | IOException ex) {
            throw mapped(ex);
        }
    }

    public JsonNode postMultipart(
            String path, byte[] image, String filename, String cropCode, String correlationId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", namedResource(image, filename));
        body.add("crop_code", cropCode);
        return exchangeMultipartJson(path, body, correlationId);
    }

    public JsonNode postMultipartAudio(String path, byte[] audio, String filename, String correlationId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", namedResource(audio, filename));
        return exchangeMultipartJson(path, body, correlationId);
    }

    public ExplanationResult postMultipartPng(
            String path,
            byte[] image,
            String filename,
            String cropCode,
            String targetLabel,
            String correlationId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", namedResource(image, filename));
        body.add("crop_code", cropCode);
        if (targetLabel != null && !targetLabel.isBlank()) {
            body.add("target_label", targetLabel);
        }
        try {
            ResponseEntity<byte[]> entity = restClient.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(CorrelationId.HEADER, correlationId)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] png = entity.getBody() == null ? new byte[0] : entity.getBody();
            String model = header(entity, "X-Foshol-Model-Id");
            String version = header(entity, "X-Foshol-Model-Version");
            return new ExplanationResult(model, version, png, "image/png", 0);
        } catch (RestClientException ex) {
            throw mapped(ex);
        }
    }

    private static ByteArrayResource namedResource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private JsonNode exchangeMultipartJson(String path, MultiValueMap<String, Object> body, String correlationId) {
        try {
            String response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(CorrelationId.HEADER, correlationId)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(response);
        } catch (RestClientException | IOException ex) {
            throw mapped(ex);
        }
    }

    private SidecarFailureException mapped(Exception ex) {
        SidecarFailureException failure = SidecarProblemMapper.map(ex, mapper);
        log.warn("sidecar HTTP failed errorCode={}", failure.errorCode());
        return failure;
    }

    private static String header(ResponseEntity<byte[]> entity, String name) {
        String value = entity.getHeaders().getFirst(name);
        return value == null ? "" : value;
    }

    public byte[] readBytes(String objectKey) {
        return objectStore.read(objectKey);
    }

    public String readBase64(String objectKey) {
        return Base64.getEncoder().encodeToString(objectStore.read(objectKey));
    }

    public ObjectNode object() {
        return mapper.createObjectNode();
    }
}

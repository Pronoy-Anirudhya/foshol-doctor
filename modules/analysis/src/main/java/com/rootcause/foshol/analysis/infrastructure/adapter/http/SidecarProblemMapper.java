package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.SidecarClientException;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.ErrorCodes;
import java.util.Set;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

final class SidecarProblemMapper {

    private static final Set<String> KNOWN_CODES = Set.of(
            ErrorCodes.ERR_SIDECAR_BAD_REQUEST,
            ErrorCodes.ERR_SIDECAR_BUSY,
            ErrorCodes.ERR_SIDECAR_EMBED_DIM_MISMATCH,
            ErrorCodes.ERR_SIDECAR_FIXTURE_MISSING,
            ErrorCodes.ERR_SIDECAR_INFERENCE_FAILED,
            ErrorCodes.ERR_SIDECAR_MODEL_UNAVAILABLE,
            ErrorCodes.ERR_SIDECAR_PAYLOAD_TOO_LARGE,
            ErrorCodes.ERR_SIDECAR_UNDECODABLE,
            ErrorCodes.ERR_SIDECAR_UNKNOWN_CROP,
            ErrorCodes.ERR_SIDECAR_UNSUPPORTED_MEDIA,
            ErrorCodes.ERR_SIDECAR_UNAVAILABLE,
            ErrorCodes.ERR_SIDECAR_WARMING_UP);

    private SidecarProblemMapper() {}

    static SidecarFailureException map(Exception ex, ObjectMapper mapper) {
        if (ex instanceof RestClientResponseException response) {
            String code = codeOf(response, mapper);
            String detail = detailOf(response, mapper, ex.getMessage());
            String message = "Sidecar HTTP " + response.getStatusCode().value() + " " + code + ": " + detail;
            SidecarFailureException failure = clientError(code)
                    ? new SidecarClientException(code, message, ex)
                    : new SidecarFailureException(code, message, ex);
            return failure;
        }
        return new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "Sidecar HTTP failed", ex);
    }

    private static String codeOf(RestClientResponseException response, ObjectMapper mapper) {
        String fromBody = readField(response, mapper, "code");
        if (fromBody != null && KNOWN_CODES.contains(fromBody)) {
            if (ErrorCodes.ERR_SIDECAR_WARMING_UP.equals(fromBody)) {
                return ErrorCodes.ERR_SIDECAR_UNAVAILABLE;
            }
            return fromBody;
        }
        return fromStatus(response.getStatusCode());
    }

    private static String fromStatus(HttpStatusCode status) {
        int value = status.value();
        if (value == 400) {
            return ErrorCodes.ERR_SIDECAR_BAD_REQUEST;
        }
        if (value == 404) {
            return ErrorCodes.ERR_SIDECAR_FIXTURE_MISSING;
        }
        if (value == 413) {
            return ErrorCodes.ERR_SIDECAR_PAYLOAD_TOO_LARGE;
        }
        if (value == 415) {
            return ErrorCodes.ERR_SIDECAR_UNSUPPORTED_MEDIA;
        }
        if (value == 422) {
            return ErrorCodes.ERR_SIDECAR_UNDECODABLE;
        }
        if (value == 429) {
            return ErrorCodes.ERR_SIDECAR_BUSY;
        }
        if (value == 500) {
            return ErrorCodes.ERR_SIDECAR_INFERENCE_FAILED;
        }
        return ErrorCodes.ERR_SIDECAR_UNAVAILABLE;
    }

    private static boolean clientError(String code) {
        return ErrorCodes.ERR_SIDECAR_BAD_REQUEST.equals(code)
                || ErrorCodes.ERR_SIDECAR_UNKNOWN_CROP.equals(code)
                || ErrorCodes.ERR_SIDECAR_UNDECODABLE.equals(code)
                || ErrorCodes.ERR_SIDECAR_UNSUPPORTED_MEDIA.equals(code)
                || ErrorCodes.ERR_SIDECAR_PAYLOAD_TOO_LARGE.equals(code)
                || ErrorCodes.ERR_SIDECAR_FIXTURE_MISSING.equals(code);
    }

    private static String detailOf(RestClientResponseException response, ObjectMapper mapper, String fallback) {
        String detail = readField(response, mapper, "detail");
        return detail == null || detail.isBlank() ? fallback : detail;
    }

    private static String readField(RestClientResponseException response, ObjectMapper mapper, String field) {
        String body = response.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception ignored) {
            return null;
        }
    }
}

package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.SidecarClientException;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.contract.ErrorCodes;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

class SidecarProblemMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preservesUndecodableFromProblemJson() {
        SidecarFailureException failure =
                SidecarProblemMapper.map(response(422, ErrorCodes.ERR_SIDECAR_UNDECODABLE), mapper);
        assertThat(failure.errorCode()).isEqualTo(ErrorCodes.ERR_SIDECAR_UNDECODABLE);
        assertThat(failure).isInstanceOf(SidecarClientException.class);
        assertThat(failure.clientError()).isTrue();
        assertThat(failure.retryable()).isFalse();
    }

    @Test
    void mapsWarmingUpToUnavailable() {
        SidecarFailureException failure =
                SidecarProblemMapper.map(response(503, ErrorCodes.ERR_SIDECAR_WARMING_UP), mapper);
        assertThat(failure.errorCode()).isEqualTo(ErrorCodes.ERR_SIDECAR_UNAVAILABLE);
        assertThat(failure.retryable()).isTrue();
    }

    @Test
    void mapsBusyFromStatusWhenBodyHasNoCode() {
        RestClientResponseException ex = new RestClientResponseException(
                "busy",
                HttpStatusCode.valueOf(429),
                "Too Many Requests",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        SidecarFailureException failure = SidecarProblemMapper.map(ex, mapper);
        assertThat(failure.errorCode()).isEqualTo(ErrorCodes.ERR_SIDECAR_BUSY);
        assertThat(failure.retryable()).isTrue();
    }

    @Test
    void transportFailureIsUnavailable() {
        SidecarFailureException failure = SidecarProblemMapper.map(new ConnectException("refused"), mapper);
        assertThat(failure.errorCode()).isEqualTo(ErrorCodes.ERR_SIDECAR_UNAVAILABLE);
    }

    @Test
    void unknownCropIsClientError() {
        SidecarFailureException failure =
                SidecarProblemMapper.map(response(400, ErrorCodes.ERR_SIDECAR_UNKNOWN_CROP), mapper);
        assertThat(failure.errorCode()).isEqualTo(ErrorCodes.ERR_SIDECAR_UNKNOWN_CROP);
        assertThat(failure.clientError()).isTrue();
        assertThat(failure.expectedExplainFailure()).isTrue();
    }

    private static RestClientResponseException response(int status, String code) {
        String body = "{\"code\":\"" + code + "\",\"detail\":\"x\",\"status\":" + status + "}";
        return new RestClientResponseException(
                "problem",
                HttpStatusCode.valueOf(status),
                "error",
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }
}

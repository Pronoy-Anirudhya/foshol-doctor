package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.ExplanationResult;
import com.rootcause.foshol.analysis.application.port.ObjectStorePort;
import com.rootcause.foshol.analysis.application.port.SidecarClientException;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.ErrorCodes;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class SidecarHttpClientTest {

    @Mock
    ObjectStorePort objectStore;

    private HttpServer server;
    private SidecarHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/asr/transcribe", exchange -> {
            byte[] body = """
                    {"type":"about:blank","title":"Undecodable payload","status":422,\
                    "detail":"WAV bytes could not be decoded","code":"ERR_SIDECAR_UNDECODABLE"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(422, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v1/vision/explain", exchange -> {
            byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.getResponseHeaders().add("X-Foshol-Model-Id", "vit");
            exchange.getResponseHeaders().add("X-Foshol-Model-Version", "1");
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.start();
        RestClient rest = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        client = new SidecarHttpClient(rest, mapper, objectStore);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void multipartAudioPreservesSidecarProblemCode() {
        assertThatThrownBy(() -> client.postMultipartAudio("/v1/asr/transcribe", new byte[] {1}, "audio.bin", "corr"))
                .isInstanceOf(SidecarClientException.class)
                .extracting(ex -> ((SidecarFailureException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_SIDECAR_UNDECODABLE);
    }

    @Test
    void explainReturnsPngAndModelHeaders() {
        ExplanationResult result =
                client.postMultipartPng("/v1/vision/explain", new byte[] {1, 2}, "leaf.png", "rice", "Blast", "corr");
        assertThat(result.modelId()).isEqualTo("vit");
        assertThat(result.modelVersion()).isEqualTo("1");
        assertThat(result.overlayPng()[0]).isEqualTo((byte) 0x89);
        assertThat(result.contentType()).isEqualTo("image/png");
    }
}

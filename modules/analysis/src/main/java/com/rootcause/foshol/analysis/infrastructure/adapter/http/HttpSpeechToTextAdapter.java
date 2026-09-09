package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TranscriptRequest;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.dto.TranscribeResponse;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ConfigKeys;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
@ConditionalOnProperty(name = ConfigKeys.AI_MODE, havingValue = "live")
public class HttpSpeechToTextAdapter implements SpeechToTextPort {

    private static final String PATH = "/ai/transcribe";

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;

    public HttpSpeechToTextAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public TranscriptResult transcribe(TranscriptRequest request) {
        return sidecar.execute("asr", () -> {
            long started = System.nanoTime();
            byte[] bytes = http.readBytes(request.objectKey());
            MultiValueMap<String, HttpEntity<?>> multipart = new LinkedMultiValueMap<>();
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.parseMediaType("audio/wav"));
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return request.audioId().toString() + ".wav";
                }
            };
            multipart.add("file", new HttpEntity<>(resource, partHeaders));

            TranscribeResponse body =
                    http.postMultipart(PATH, multipart, request.correlationId(), TranscribeResponse.class);

            int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000L);
            String modelId = body.modelId() == null ? "" : body.modelId();
            String text = body.text() == null ? "" : body.text();
            return new TranscriptResult(modelId, text, BigDecimal.ZERO, latencyMs);
        });
    }
}

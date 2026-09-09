package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.rootcause.foshol.analysis.application.port.RawCandidate;
import com.rootcause.foshol.analysis.application.port.VisionBatchRequest;
import com.rootcause.foshol.analysis.application.port.VisionBatchResult;
import com.rootcause.foshol.analysis.application.port.VisionImageRef;
import com.rootcause.foshol.analysis.application.port.VisionImageResult;
import com.rootcause.foshol.analysis.application.port.VisionModelPort;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.dto.DetectDiseaseResponse;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.dto.DetectDiseaseResponse.ImageResultDto;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.dto.DetectDiseaseResponse.PredictionDto;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ConfigKeys;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class HttpVisionModelAdapter implements VisionModelPort {

    private static final Logger log = LoggerFactory.getLogger(HttpVisionModelAdapter.class);
    private static final String PATH = "/ai/detect-disease";
    private static final int MAX_IMAGES = 3;

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;

    public HttpVisionModelAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public VisionResult classify(VisionRequest request) {
        VisionBatchResult batch = classifyBatch(new VisionBatchRequest(
                request.caseId(),
                request.cropCode(),
                request.correlationId(),
                List.of(new VisionImageRef(request.imageId(), request.objectKey(), request.sha256()))));
        VisionImageResult only = batch.images().getFirst();
        return new VisionResult(batch.modelId(), batch.modelVersion(), only.candidates(), batch.latencyMs());
    }

    @Override
    public VisionBatchResult classifyBatch(VisionBatchRequest request) {
        return sidecar.execute("vision", () -> doClassifyBatch(request));
    }

    private VisionBatchResult doClassifyBatch(VisionBatchRequest request) {
        List<VisionImageRef> images = request.images() == null ? List.of() : request.images();
        if (images.isEmpty() || images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("vision batch size must be 1.." + MAX_IMAGES);
        }

        long started = System.nanoTime();
        MultiValueMap<String, HttpEntity<?>> multipart = new LinkedMultiValueMap<>();
        Map<String, VisionImageRef> byId = new LinkedHashMap<>();
        for (VisionImageRef image : images) {
            byte[] bytes = http.readBytes(image.objectKey());
            String partName = image.imageId().toString();
            byId.put(partName, image);
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.IMAGE_JPEG);
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return partName;
                }
            };
            multipart.add("files", new HttpEntity<>(resource, partHeaders));
        }

        log.info(
                "vision live request caseId={} imageCount={} correlationId={} path={}",
                request.caseId(),
                images.size(),
                request.correlationId(),
                PATH);
        DetectDiseaseResponse body =
                http.postMultipart(PATH, multipart, request.correlationId(), DetectDiseaseResponse.class);
        log.info("vision live response: {}", body);

        List<VisionImageResult> mapped = new ArrayList<>();
        if (body.results() != null) {
            int index = 0;
            for (ImageResultDto row : body.results()) {
                VisionImageRef ref = row.imageId() == null ? null : byId.get(row.imageId());
                if (ref == null && index < images.size()) {
                    ref = images.get(index);
                }
                index++;
                if (ref == null) {
                    continue;
                }
                mapped.add(new VisionImageResult(ref.imageId(), ref.sha256(), toCandidates(row.predictions())));
            }
        }

        int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000L);
        String modelId = body.modelId() == null ? "" : body.modelId();
        String modelVersion = body.modelVersion() == null ? "" : body.modelVersion();
        VisionBatchResult result = new VisionBatchResult(modelId, modelVersion, List.copyOf(mapped), latencyMs);
        log.info(
                "vision live response caseId={} modelId={} modelVersion={} latencyMs={} correlationId={} results=[{}]",
                request.caseId(),
                result.modelId(),
                result.modelVersion(),
                result.latencyMs(),
                request.correlationId(),
                summarise(result.images()));
        return result;
    }

    private static String summarise(List<VisionImageResult> images) {
        return images.stream()
                .map(image -> {
                    String top = image.candidates().isEmpty()
                            ? "none"
                            : image.candidates().getFirst().rawLabel()
                                    + "="
                                    + image.candidates().getFirst().confidence();
                    return image.imageId() + ":" + top;
                })
                .collect(Collectors.joining(", "));
    }

    private static List<RawCandidate> toCandidates(List<PredictionDto> predictions) {
        if (predictions == null) {
            return List.of();
        }
        List<RawCandidate> out = new ArrayList<>(predictions.size());
        for (PredictionDto prediction : predictions) {
            out.add(new RawCandidate(prediction.label(), BigDecimal.valueOf(prediction.score())));
        }
        return List.copyOf(out);
    }
}

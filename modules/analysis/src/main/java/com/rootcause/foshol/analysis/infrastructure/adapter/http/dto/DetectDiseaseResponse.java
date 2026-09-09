package com.rootcause.foshol.analysis.infrastructure.adapter.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DetectDiseaseResponse(
        @JsonProperty("model_id") String modelId,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("correlation_id") String correlationId,
        List<ImageResultDto> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageResultDto(
            @JsonProperty("image_id") String imageId,
            String sha256,
            String filename,
            List<PredictionDto> predictions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PredictionDto(String label, double score) {}
}

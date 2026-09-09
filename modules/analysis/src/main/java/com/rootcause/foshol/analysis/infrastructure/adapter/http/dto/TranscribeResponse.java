package com.rootcause.foshol.analysis.infrastructure.adapter.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscribeResponse(
        @JsonProperty("model_id") String modelId,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("correlation_id") String correlationId,
        String text) {}

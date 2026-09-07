package com.rootcause.foshol.analysis.application.port;

public interface TextEmbeddingPort {

    EmbeddingResult embed(EmbeddingRequest request);
}

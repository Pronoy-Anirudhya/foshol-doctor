package com.rootcause.foshol.analysis.application.port;

public interface ExplainabilityPort {

    ExplanationResult explain(ExplanationRequest request);
}

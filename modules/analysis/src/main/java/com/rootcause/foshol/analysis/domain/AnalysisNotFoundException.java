package com.rootcause.foshol.analysis.domain;

public class AnalysisNotFoundException extends AnalysisException {

    public AnalysisNotFoundException(String errorCode) {
        super(errorCode, 404, "Analysis was not found for the case.");
    }
}

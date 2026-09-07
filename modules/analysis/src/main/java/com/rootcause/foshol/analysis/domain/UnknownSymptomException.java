package com.rootcause.foshol.analysis.domain;

public class UnknownSymptomException extends AnalysisException {

    public UnknownSymptomException(String errorCode) {
        super(errorCode, 400, "One or more symptom identifiers are unknown.");
    }
}

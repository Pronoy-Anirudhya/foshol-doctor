package com.rootcause.foshol.analysis.domain;

public class AnalysisException extends RuntimeException {

    private final String errorCode;
    private final int status;

    public AnalysisException(String errorCode, int status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String errorCode() {
        return errorCode;
    }

    public int status() {
        return status;
    }
}

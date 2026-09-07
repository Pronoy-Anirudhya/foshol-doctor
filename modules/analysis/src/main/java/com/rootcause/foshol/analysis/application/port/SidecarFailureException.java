package com.rootcause.foshol.analysis.application.port;

public class SidecarFailureException extends RuntimeException {

    private final String errorCode;

    public SidecarFailureException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SidecarFailureException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}

package com.rootcause.foshol.analysis.application.port;

public class SidecarClientException extends SidecarFailureException {

    public SidecarClientException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

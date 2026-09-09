package com.rootcause.foshol.analysis.application.port;

import com.rootcause.foshol.common.ErrorCodes;

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

    public boolean retryable() {
        return ErrorCodes.ERR_SIDECAR_UNAVAILABLE.equals(errorCode)
                || ErrorCodes.ERR_SIDECAR_BUSY.equals(errorCode)
                || ErrorCodes.ERR_SIDECAR_WARMING_UP.equals(errorCode)
                || ErrorCodes.ERR_SIDECAR_INFERENCE_FAILED.equals(errorCode);
    }

    public boolean clientError() {
        return ErrorCodes.ERR_SIDECAR_BAD_REQUEST.equals(errorCode)
                || ErrorCodes.ERR_SIDECAR_UNKNOWN_CROP.equals(errorCode)
                || ErrorCodes.ERR_SIDECAR_UNDECODABLE.equals(errorCode)
                || ErrorCodes.ERR_SIDECAR_UNSUPPORTED_MEDIA.equals(errorCode)
                || ErrorCodes.ERR_SIDECAR_PAYLOAD_TOO_LARGE.equals(errorCode)
                || ErrorCodes.ERR_SIDECAR_FIXTURE_MISSING.equals(errorCode);
    }

    public boolean expectedExplainFailure() {
        return clientError() || ErrorCodes.ERR_SIDECAR_MODEL_UNAVAILABLE.equals(errorCode);
    }
}

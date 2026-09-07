package com.rootcause.foshol.identity.domain;

public class IdentityException extends RuntimeException {

    private final String errorCode;
    private final int status;

    public IdentityException(String errorCode, int status, String message) {
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

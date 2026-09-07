package com.rootcause.foshol.intake.application;

public class IntakeException extends RuntimeException {

    private final String errorCode;
    private final int status;
    private final Object extra;

    public IntakeException(String errorCode, int status, String message) {
        this(errorCode, status, message, null);
    }

    public IntakeException(String errorCode, int status, String message, Object extra) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.extra = extra;
    }

    public String errorCode() {
        return errorCode;
    }

    public int status() {
        return status;
    }

    public Object extra() {
        return extra;
    }
}

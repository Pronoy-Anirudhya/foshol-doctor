package com.rootcause.foshol.knowledge.domain;

public class KnowledgeException extends RuntimeException {

    private final String errorCode;
    private final int status;

    public KnowledgeException(String errorCode, int status, String message) {
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

package com.rootcause.foshol.intake.application.port;

public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(Throwable cause) {
        super("idempotency key already stored", cause);
    }
}

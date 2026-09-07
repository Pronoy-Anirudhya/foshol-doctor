package com.rootcause.foshol.intake.domain.vo;

import java.util.Objects;

public record ObjectKey(String value) {

    public static final int MAX_LENGTH = 200;

    public ObjectKey {
        Objects.requireNonNull(value, "object key");
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("object key must be 1.." + MAX_LENGTH + " characters");
        }
    }

    public static ObjectKey imageOriginal(CaseId caseId, ImageId imageId, String extension) {
        return new ObjectKey("cases/" + caseId.value() + "/img/" + imageId.value() + "." + extension);
    }

    public static ObjectKey imageDerivative(CaseId caseId, ImageId imageId) {
        return new ObjectKey("cases/" + caseId.value() + "/img/" + imageId.value() + "-d.jpg");
    }

    public static ObjectKey audio(CaseId caseId, AudioId audioId, String extension) {
        return new ObjectKey("cases/" + caseId.value() + "/audio/" + audioId.value() + "." + extension);
    }

    @Override
    public String toString() {
        return value;
    }
}

package com.rootcause.foshol.intake.domain.vo;

import com.rootcause.foshol.common.Uuid7;
import java.util.Objects;
import java.util.UUID;

public record CaseId(UUID value) {

    public CaseId {
        Objects.requireNonNull(value, "case id");
    }

    public static CaseId newId() {
        return new CaseId(Uuid7.create());
    }

    public static CaseId of(UUID value) {
        return new CaseId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

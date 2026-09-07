package com.rootcause.foshol.knowledge.domain;

import java.util.UUID;

public record SymptomRef(UUID id, String code, String nameBn) {

    public SymptomRef {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        code = code == null ? "" : code;
        nameBn = nameBn == null ? "" : nameBn;
    }
}

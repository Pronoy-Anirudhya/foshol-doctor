package com.rootcause.foshol.intake.domain.vo;

import com.rootcause.foshol.common.util.Uuid7;
import java.util.Objects;
import java.util.UUID;

public record AudioId(UUID value) {

    public AudioId {
        Objects.requireNonNull(value, "audio id");
    }

    public static AudioId newId() {
        return new AudioId(Uuid7.create());
    }

    public static AudioId of(UUID value) {
        return new AudioId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

package com.rootcause.foshol.intake.domain.vo;

import com.rootcause.foshol.common.util.Uuid7;
import java.util.Objects;
import java.util.UUID;

public record ImageId(UUID value) {

    public ImageId {
        Objects.requireNonNull(value, "image id");
    }

    public static ImageId newId() {
        return new ImageId(Uuid7.create());
    }

    public static ImageId of(UUID value) {
        return new ImageId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

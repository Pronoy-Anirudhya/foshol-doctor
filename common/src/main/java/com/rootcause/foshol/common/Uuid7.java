package com.rootcause.foshol.common;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public final class Uuid7 {

    private Uuid7() {}

    public static UUID create() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}

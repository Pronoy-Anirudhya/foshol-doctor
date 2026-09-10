package com.rootcause.foshol.knowledge.domain.spec;

import java.time.Instant;

public final class LiveContentSpec {

    private LiveContentSpec() {}

    public static boolean isLive(Instant deletedAt) {
        return deletedAt == null;
    }
}

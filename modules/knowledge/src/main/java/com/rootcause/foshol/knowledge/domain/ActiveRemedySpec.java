package com.rootcause.foshol.knowledge.domain;

import java.time.Instant;

public final class ActiveRemedySpec {

    private ActiveRemedySpec() {}

    public static boolean isSatisfiedBy(boolean active, Instant deletedAt) {
        return active && LiveContentSpec.isLive(deletedAt);
    }
}

package com.rootcause.foshol.intake.domain.spec;

import com.rootcause.foshol.intake.domain.IdempotencyRecord;
import com.rootcause.foshol.intake.domain.vo.RequestFingerprint;
import java.util.UUID;

public final class IdempotencyReplaySpec {

    public static final IdempotencyReplaySpec INSTANCE = new IdempotencyReplaySpec();

    private IdempotencyReplaySpec() {}

    public boolean isReplay(IdempotencyRecord stored, UUID farmerId, RequestFingerprint fingerprint) {
        return stored != null
                && stored.farmerId().equals(farmerId)
                && stored.requestHash().equals(fingerprint.hash());
    }

    public boolean isConflict(IdempotencyRecord stored, UUID farmerId, RequestFingerprint fingerprint) {
        if (stored == null) {
            return false;
        }
        return !isReplay(stored, farmerId, fingerprint);
    }
}

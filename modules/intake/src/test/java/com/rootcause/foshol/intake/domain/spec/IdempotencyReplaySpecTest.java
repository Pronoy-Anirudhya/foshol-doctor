package com.rootcause.foshol.intake.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.enums.FieldAreaUnit;
import com.rootcause.foshol.intake.domain.IdempotencyRecord;
import com.rootcause.foshol.intake.domain.vo.RequestFingerprint;
import com.rootcause.foshol.intake.domain.vo.Sha256;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyReplaySpecTest {

    @Test
    void replayConflictAndFarmerMismatch() {
        UUID farmer = UUID.fromString("01800000-0000-7000-8000-000000000201");
        UUID other = UUID.fromString("01800000-0000-7000-8000-000000000299");
        UUID crop = UUID.fromString("01800000-0000-7000-8000-000000000001");
        RequestFingerprint fingerprint = RequestFingerprint.compute(
                farmer, crop, "", null, BigDecimal.ONE, FieldAreaUnit.DECIMAL, null, null, List.of(Sha256.ofUtf8("img")), null);
        IdempotencyRecord stored = new IdempotencyRecord(
                UUID.randomUUID(),
                farmer,
                "POST /api/v1/cases",
                fingerprint.hash(),
                202,
                "{}",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(IdempotencyReplaySpec.INSTANCE.isReplay(stored, farmer, fingerprint)).isTrue();
        RequestFingerprint otherHash = RequestFingerprint.compute(
                farmer, crop, "x", null, BigDecimal.ONE, FieldAreaUnit.DECIMAL, null, null, List.of(Sha256.ofUtf8("img")), null);
        assertThat(IdempotencyReplaySpec.INSTANCE.isConflict(stored, farmer, otherHash)).isTrue();
        assertThat(IdempotencyReplaySpec.INSTANCE.isConflict(stored, other, fingerprint)).isTrue();
    }
}

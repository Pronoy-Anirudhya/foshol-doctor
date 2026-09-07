package com.rootcause.foshol.intake.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    @Test
    void isStableForTheSameContent() {
        UUID farmer = UUID.fromString("01800000-0000-7000-8000-000000000201");
        UUID crop = UUID.fromString("01800000-0000-7000-8000-000000000001");
        Sha256 image = Sha256.ofUtf8("img");
        RequestFingerprint a = RequestFingerprint.compute(farmer, crop, "নোট", null, List.of(image), null);
        RequestFingerprint b = RequestFingerprint.compute(farmer, crop, "নোট", null, List.of(image), null);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void changesWhenAnyContentChanges() {
        UUID farmer = UUID.fromString("01800000-0000-7000-8000-000000000201");
        UUID crop = UUID.fromString("01800000-0000-7000-8000-000000000001");
        Sha256 image = Sha256.ofUtf8("img");
        RequestFingerprint base = RequestFingerprint.compute(farmer, crop, "", null, List.of(image), null);
        assertThat(RequestFingerprint.compute(farmer, crop, "x", null, List.of(image), null)).isNotEqualTo(base);
        assertThat(RequestFingerprint.compute(farmer, crop, "", crop, List.of(image), null)).isNotEqualTo(base);
        assertThat(RequestFingerprint.compute(farmer, crop, "", null, List.of(Sha256.ofUtf8("other")), null))
                .isNotEqualTo(base);
    }
}

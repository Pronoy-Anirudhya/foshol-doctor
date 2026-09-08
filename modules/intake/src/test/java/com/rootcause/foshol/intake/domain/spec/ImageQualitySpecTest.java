package com.rootcause.foshol.intake.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.intake.domain.QualityReason;
import com.rootcause.foshol.intake.domain.vo.ImageMetrics;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ImageQualitySpecTest {

    private final ImageQualitySpec spec = new ImageQualitySpec(60, 0.15, 0.90, 224, 0.12);

    @Test
    void rejectsTinyImages() {
        assertThat(spec.verdict(new ImageMetrics(100, 100, 200, 0.5, 0.5)).reason())
                .isEqualTo(QualityReason.TOO_SMALL);
        assertThat(spec.verdict(new ImageMetrics(300, 300, 200, 0.5, 0.5)).accepted()).isTrue();
    }

    @Test
    void rejectsBlurryUnderexposedAndOverexposed() {
        assertThat(spec.verdict(new ImageMetrics(300, 300, 10, 0.5, 0.5)).reason()).isEqualTo(QualityReason.BLURRY);
        assertThat(spec.verdict(new ImageMetrics(300, 300, 200, 0.05, 0.5)).reason())
                .isEqualTo(QualityReason.TOO_DARK);
        assertThat(spec.verdict(new ImageMetrics(300, 300, 200, 0.95, 0.5)).reason())
                .isEqualTo(QualityReason.TOO_BRIGHT);
    }

    @Test
    void rejectsLowVegetationAsNotACrop() {
        assertThat(spec.verdict(new ImageMetrics(300, 300, 200, 0.5, 0.05)).reason())
                .isEqualTo(QualityReason.NOT_A_CROP);
        assertThat(spec.verdict(new ImageMetrics(300, 300, 200, 0.5, 0.12)).accepted()).isTrue();
    }

    @Test
    void qualityScoreIsMonotonicInBlur() {
        BigDecimal low = spec.qualityScore(new ImageMetrics(300, 300, 80, 0.525, 0.5));
        BigDecimal high = spec.qualityScore(new ImageMetrics(300, 300, 800, 0.525, 0.5));
        assertThat(high).isGreaterThan(low);
        assertThat(high).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }
}

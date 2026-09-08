package com.rootcause.foshol.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.intake.IntakeFixtures;
import com.rootcause.foshol.intake.domain.vo.ImageMetrics;
import org.junit.jupiter.api.Test;

class ImageMetricsCalculatorTest {

    @Test
    void sharpHasHigherBlurVarianceThanBlurred() {
        ImageMetrics sharp = ImageMetricsCalculator.calculate(IntakeFixtures.sharpJpeg());
        ImageMetrics blurred = ImageMetricsCalculator.calculate(IntakeFixtures.blurredJpeg());
        assertThat(sharp.width()).isGreaterThanOrEqualTo(224);
        assertThat(sharp.blurVariance()).isGreaterThan(blurred.blurVariance());
        assertThat(sharp.exposureScore()).isBetween(0.15, 0.90);
        assertThat(sharp.vegetationCoverage()).isGreaterThan(0.12);
    }

    @Test
    void nonCropHasLowVegetationCoverage() {
        ImageMetrics nonCrop = ImageMetricsCalculator.calculate(IntakeFixtures.nonCropJpeg());
        assertThat(nonCrop.vegetationCoverage()).isLessThan(0.12);
    }
}

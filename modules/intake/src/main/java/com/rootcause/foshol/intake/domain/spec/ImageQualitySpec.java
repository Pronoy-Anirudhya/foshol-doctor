package com.rootcause.foshol.intake.domain.spec;

import com.rootcause.foshol.intake.domain.QualityReason;
import com.rootcause.foshol.intake.domain.vo.ImageMetrics;
import com.rootcause.foshol.intake.domain.vo.ImageQuality;
import com.rootcause.foshol.intake.domain.vo.QualityVerdict;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ImageQualitySpec {

    private final double blurVarianceMin;
    private final double exposureMin;
    private final double exposureMax;
    private final int minEdgePx;
    private final double vegetationCoverageMin;

    public ImageQualitySpec(
            double blurVarianceMin,
            double exposureMin,
            double exposureMax,
            int minEdgePx,
            double vegetationCoverageMin) {
        this.blurVarianceMin = blurVarianceMin;
        this.exposureMin = exposureMin;
        this.exposureMax = exposureMax;
        this.minEdgePx = minEdgePx;
        this.vegetationCoverageMin = vegetationCoverageMin;
    }

    public QualityVerdict verdict(ImageMetrics metrics) {
        if (metrics.width() <= 0 || metrics.height() <= 0) {
            return QualityVerdict.reject(QualityReason.UNREADABLE);
        }
        if (metrics.shorterEdge() < minEdgePx) {
            return QualityVerdict.reject(QualityReason.TOO_SMALL);
        }
        if (metrics.vegetationCoverage() < vegetationCoverageMin) {
            return QualityVerdict.reject(QualityReason.NOT_A_CROP);
        }
        if (metrics.blurVariance() < blurVarianceMin) {
            return QualityVerdict.reject(QualityReason.BLURRY);
        }
        if (metrics.exposureScore() < exposureMin) {
            return QualityVerdict.reject(QualityReason.TOO_DARK);
        }
        if (metrics.exposureScore() > exposureMax) {
            return QualityVerdict.reject(QualityReason.TOO_BRIGHT);
        }
        return QualityVerdict.accept();
    }

    public BigDecimal qualityScore(ImageMetrics metrics) {
        double blurNorm = metrics.blurVariance() / (metrics.blurVariance() + blurVarianceMin);
        double midpoint = (exposureMin + exposureMax) / 2.0;
        double halfWidth = (exposureMax - exposureMin) / 2.0;
        double exposureNorm = 1.0 - Math.abs(metrics.exposureScore() - midpoint) / halfWidth;
        if (exposureNorm < 0.0) {
            exposureNorm = 0.0;
        }
        if (exposureNorm > 1.0) {
            exposureNorm = 1.0;
        }
        double score = blurNorm * exposureNorm;
        return BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
    }

    public ImageQuality quality(ImageMetrics metrics) {
        return new ImageQuality(
                metrics.blurVariance(),
                metrics.exposureScore(),
                qualityScore(metrics),
                metrics.width(),
                metrics.height());
    }
}

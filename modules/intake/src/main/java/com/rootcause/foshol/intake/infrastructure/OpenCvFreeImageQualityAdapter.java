package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.intake.application.port.ImageQualityPort;
import com.rootcause.foshol.intake.domain.vo.ImageMetrics;
import org.springframework.stereotype.Component;

@Component
public class OpenCvFreeImageQualityAdapter implements ImageQualityPort {

    @Override
    public ImageProbe probe(byte[] bytes) {
        ImageMetrics metrics = ImageMetricsCalculator.calculate(bytes);
        return new ImageProbe(
                metrics.width(), metrics.height(), metrics.blurVariance(), metrics.exposureScore());
    }
}

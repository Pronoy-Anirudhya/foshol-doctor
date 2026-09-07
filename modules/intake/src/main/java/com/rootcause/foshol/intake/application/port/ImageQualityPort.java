package com.rootcause.foshol.intake.application.port;

public interface ImageQualityPort {

    ImageProbe probe(byte[] bytes);

    record ImageProbe(int width, int height, double blurVariance, double exposureScore) {}
}

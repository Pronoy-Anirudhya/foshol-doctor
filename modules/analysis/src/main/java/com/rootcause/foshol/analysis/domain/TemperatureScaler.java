package com.rootcause.foshol.analysis.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class TemperatureScaler {

    private TemperatureScaler() {}

    public static List<RawCandidate> rescale(List<RawCandidate> input, BigDecimal temperature) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        if (temperature == null || temperature.compareTo(BigDecimal.ONE) == 0) {
            return List.copyOf(input);
        }
        double invT = 1.0d / temperature.doubleValue();
        double[] powered = new double[input.size()];
        double sum = 0.0d;
        for (int i = 0; i < input.size(); i++) {
            powered[i] = Math.pow(input.get(i).confidence().doubleValue(), invT);
            sum += powered[i];
        }
        List<RawCandidate> out = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            double p = sum == 0.0d ? 0.0d : powered[i] / sum;
            out.add(new RawCandidate(input.get(i).rawLabel(), AnalysisScale.confidence(BigDecimal.valueOf(p))));
        }
        return List.copyOf(out);
    }
}

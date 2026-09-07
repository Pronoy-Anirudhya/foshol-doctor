package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.intake.domain.vo.ImageMetrics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class ImageMetricsCalculator {

    private ImageMetricsCalculator() {}

    public static ImageMetrics calculate(byte[] bytes) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            return new ImageMetrics(0, 0, 0, 0);
        }
        if (image == null) {
            return new ImageMetrics(0, 0, 0, 0);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] grey = new int[width * height];
        long sum = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int value = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                grey[y * width + x] = value;
                sum += value;
            }
        }
        double exposure = width * height == 0 ? 0 : (sum / (double) (width * height)) / 255.0;
        int count = 0;
        double mean = 0;
        double[] samples = new double[Math.max(1, (width - 2) * (height - 2))];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int center = grey[y * width + x];
                int lap = grey[(y - 1) * width + x]
                        + grey[(y + 1) * width + x]
                        + grey[y * width + (x - 1)]
                        + grey[y * width + (x + 1)]
                        - 4 * center;
                samples[count++] = lap;
                mean += lap;
            }
        }
        if (count == 0) {
            return new ImageMetrics(width, height, 0, exposure);
        }
        mean /= count;
        double var = 0;
        for (int i = 0; i < count; i++) {
            double d = samples[i] - mean;
            var += d * d;
        }
        var /= count;
        return new ImageMetrics(width, height, var, exposure);
    }
}

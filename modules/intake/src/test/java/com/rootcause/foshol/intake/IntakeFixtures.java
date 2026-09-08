package com.rootcause.foshol.intake;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.imageio.ImageIO;

public final class IntakeFixtures {

    private IntakeFixtures() {}

    public static byte[] sharpJpeg() {
        return jpeg(checkerboard(320, 320, new Color(0, 180, 0), new Color(0, 90, 0), 8));
    }

    public static byte[] nonCropJpeg() {
        return jpeg(checkerboard(320, 320, Color.BLACK, Color.WHITE, 8));
    }

    public static byte[] blurredJpeg() {
        return png(solid(320, 320, new Color(128, 128, 128)));
    }

    public static byte[] darkJpeg() {
        return jpeg(checkerboard(320, 320, Color.BLACK, new Color(30, 30, 30), 8));
    }

    public static byte[] brightJpeg() {
        return jpeg(checkerboard(320, 320, Color.WHITE, new Color(230, 230, 230), 8));
    }

    public static byte[] tinyPng() {
        return png(checkerboard(160, 160, new Color(0, 180, 0), new Color(0, 90, 0), 8));
    }

    public static byte[] pngBytes() {
        return png(checkerboard(320, 320, new Color(0, 180, 0), new Color(0, 90, 0), 8));
    }

    public static byte[] pdf() {
        return "%PDF-1.4 not an image".getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] wav2s() {
        int sampleRate = 16000;
        int samples = sampleRate * 2;
        int dataSize = samples * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        for (int i = 0; i < samples; i++) {
            buffer.putShort((short) ((i % 32) * 1000));
        }
        return buffer.array();
    }

    public static Map<String, String> imageDigests() {
        return Map.of(
                "sharp", sha(sharpJpeg()),
                "blurred", sha(blurredJpeg()),
                "png", sha(pngBytes()));
    }

    public static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static BufferedImage solid(int width, int height, Color colour) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static BufferedImage checkerboard(int width, int height, Color a, Color b, int cell) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean on = ((x / cell) + (y / cell)) % 2 == 0;
                g.setColor(on ? a : b);
                g.fillRect(x, y, 1, 1);
            }
        }
        g.dispose();
        return image;
    }

    private static byte[] jpeg(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

package com.rootcause.foshol.intake.domain;

import java.util.Optional;

public final class ContentTypeSniffer {

    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_WEBP = "image/webp";
    public static final String AUDIO_WAV = "audio/wav";
    public static final String AUDIO_WEBM = "audio/webm";
    public static final String AUDIO_OGG = "audio/ogg";
    public static final String AUDIO_MP4 = "audio/mp4";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP = {0x57, 0x45, 0x42, 0x50};
    private static final byte[] WAVE = {0x57, 0x41, 0x56, 0x45};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] OGG = {0x4F, 0x67, 0x67, 0x53};
    private static final byte[] FTYP = {0x66, 0x74, 0x79, 0x70};

    private ContentTypeSniffer() {}

    public static Optional<String> sniff(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        if (startsWith(bytes, JPEG)) {
            return Optional.of(IMAGE_JPEG);
        }
        if (startsWith(bytes, PNG)) {
            return Optional.of(IMAGE_PNG);
        }
        if (startsWith(bytes, RIFF) && at(bytes, 8, WEBP)) {
            return Optional.of(IMAGE_WEBP);
        }
        if (startsWith(bytes, RIFF) && at(bytes, 8, WAVE)) {
            return Optional.of(AUDIO_WAV);
        }
        if (startsWith(bytes, WEBM)) {
            return Optional.of(AUDIO_WEBM);
        }
        if (startsWith(bytes, OGG)) {
            return Optional.of(AUDIO_OGG);
        }
        if (at(bytes, 4, FTYP)) {
            return Optional.of(AUDIO_MP4);
        }
        return Optional.empty();
    }

    public static String extensionFor(String sniffedType) {
        return switch (sniffedType) {
            case IMAGE_JPEG -> "jpg";
            case IMAGE_PNG -> "png";
            case IMAGE_WEBP -> "webp";
            case AUDIO_WAV -> "wav";
            case AUDIO_WEBM -> "webm";
            case AUDIO_OGG -> "ogg";
            case AUDIO_MP4 -> "mp4";
            default -> throw new IllegalArgumentException("no extension for " + sniffedType);
        };
    }

    public static boolean isImage(String sniffedType) {
        return IMAGE_JPEG.equals(sniffedType)
                || IMAGE_PNG.equals(sniffedType)
                || IMAGE_WEBP.equals(sniffedType);
    }

    public static boolean isAudio(String sniffedType) {
        return AUDIO_WAV.equals(sniffedType)
                || AUDIO_WEBM.equals(sniffedType)
                || AUDIO_OGG.equals(sniffedType)
                || AUDIO_MP4.equals(sniffedType);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return at(bytes, 0, prefix);
    }

    private static boolean at(byte[] bytes, int offset, byte[] expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}

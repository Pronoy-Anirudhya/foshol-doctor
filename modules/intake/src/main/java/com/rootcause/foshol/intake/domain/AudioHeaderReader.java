package com.rootcause.foshol.intake.domain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;

public final class AudioHeaderReader {

    private AudioHeaderReader() {}

    public static OptionalInt durationMs(byte[] bytes, String sniffedType) {
        if (bytes == null || sniffedType == null) {
            return OptionalInt.empty();
        }
        if (!ContentTypeSniffer.AUDIO_WAV.equals(sniffedType) || bytes.length < 44) {
            return OptionalInt.empty();
        }
        int byteRate = ByteBuffer.wrap(bytes, 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (byteRate <= 0) {
            return OptionalInt.empty();
        }
        int data = Math.max(0, bytes.length - 44);
        return OptionalInt.of((int) ((data * 1000L) / byteRate));
    }

    public static OptionalInt sampleRateHz(byte[] bytes, String sniffedType) {
        if (bytes == null || sniffedType == null) {
            return OptionalInt.empty();
        }
        return switch (sniffedType) {
            case ContentTypeSniffer.AUDIO_WAV -> wavSampleRate(bytes);
            case ContentTypeSniffer.AUDIO_MP4 -> mp4Timescale(bytes);
            case ContentTypeSniffer.AUDIO_OGG, ContentTypeSniffer.AUDIO_WEBM -> oggSampleRate(bytes);
            default -> OptionalInt.empty();
        };
    }

    private static OptionalInt wavSampleRate(byte[] bytes) {
        if (bytes.length < 28) {
            return OptionalInt.empty();
        }
        int rate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (rate <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(rate);
    }

    private static OptionalInt mp4Timescale(byte[] bytes) {
        int index = indexOf(bytes, new byte[] {0x6D, 0x64, 0x68, 0x64});
        if (index < 0 || index + 8 >= bytes.length) {
            return OptionalInt.empty();
        }
        int version = bytes[index + 4] & 0xFF;
        int timescaleOffset = version == 1 ? index + 4 + 4 + 8 + 8 : index + 4 + 4 + 4 + 4;
        if (timescaleOffset + 4 > bytes.length) {
            return OptionalInt.empty();
        }
        int rate = ByteBuffer.wrap(bytes, timescaleOffset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if (rate <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(rate);
    }

    private static OptionalInt oggSampleRate(byte[] bytes) {
        int vorbis = indexOf(bytes, new byte[] {0x01, 0x76, 0x6F, 0x72, 0x62, 0x69, 0x73});
        if (vorbis >= 0 && vorbis + 16 <= bytes.length) {
            int rate = ByteBuffer.wrap(bytes, vorbis + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (rate > 0) {
                return OptionalInt.of(rate);
            }
        }
        int opus = indexOf(bytes, new byte[] {0x4F, 0x70, 0x75, 0x73, 0x48, 0x65, 0x61, 0x64});
        if (opus >= 0 && opus + 16 <= bytes.length) {
            int rate = ByteBuffer.wrap(bytes, opus + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (rate > 0) {
                return OptionalInt.of(rate);
            }
        }
        return OptionalInt.empty();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}

package com.rootcause.foshol.intake.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentTypeSnifferTest {

    @Test
    void recognisesDocumentedSignatures() {
        assertThat(ContentTypeSniffer.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                .contains(ContentTypeSniffer.IMAGE_JPEG);
        assertThat(ContentTypeSniffer.sniff(new byte[] {
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                }))
                .contains(ContentTypeSniffer.IMAGE_PNG);
        byte[] webp = new byte[12];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        assertThat(ContentTypeSniffer.sniff(webp)).contains(ContentTypeSniffer.IMAGE_WEBP);
        byte[] wav = webp.clone();
        wav[8] = 'W';
        wav[9] = 'A';
        wav[10] = 'V';
        wav[11] = 'E';
        assertThat(ContentTypeSniffer.sniff(wav)).contains(ContentTypeSniffer.AUDIO_WAV);
        assertThat(ContentTypeSniffer.sniff(new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}))
                .contains(ContentTypeSniffer.AUDIO_WEBM);
        assertThat(ContentTypeSniffer.sniff(new byte[] {'O', 'g', 'g', 'S'})).contains(ContentTypeSniffer.AUDIO_OGG);
        byte[] mp4 = new byte[8];
        mp4[4] = 'f';
        mp4[5] = 't';
        mp4[6] = 'y';
        mp4[7] = 'p';
        assertThat(ContentTypeSniffer.sniff(mp4)).contains(ContentTypeSniffer.AUDIO_MP4);
    }

    @Test
    void truncatedAndUnknownAreEmpty() {
        assertThat(ContentTypeSniffer.sniff(new byte[] {(byte) 0xFF})).isEmpty();
        assertThat(ContentTypeSniffer.sniff("%PDF".getBytes())).isEmpty();
    }
}

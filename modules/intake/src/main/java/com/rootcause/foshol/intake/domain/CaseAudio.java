package com.rootcause.foshol.intake.domain;

import com.rootcause.foshol.intake.domain.vo.AudioId;
import com.rootcause.foshol.intake.domain.vo.ObjectKey;
import java.math.BigDecimal;

public record CaseAudio(
        AudioId id,
        ObjectKey objectKey,
        int durationMs,
        int sampleRateHz,
        int byteSize,
        String transcriptBn,
        BigDecimal asrConfidence) {

    public CaseAudio withTranscript(String transcriptBn, BigDecimal asrConfidence) {
        return new CaseAudio(id, objectKey, durationMs, sampleRateHz, byteSize, transcriptBn, asrConfidence);
    }
}

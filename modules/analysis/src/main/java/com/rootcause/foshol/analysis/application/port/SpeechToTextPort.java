package com.rootcause.foshol.analysis.application.port;

public interface SpeechToTextPort {

    TranscriptResult transcribe(TranscriptRequest request);

    TranscriptResult transcribeAudio(byte[] audio, String language, String correlationId);
}

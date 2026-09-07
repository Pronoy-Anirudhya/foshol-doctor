package com.rootcause.foshol.analysis.application.port;

public interface SpeechToTextPort {

    TranscriptResult transcribe(TranscriptRequest request);
}

package com.rootcause.foshol.common.events;

import java.util.UUID;

public record CaseAudioRef(UUID audioId, String objectKey, int durationMs, String transcriptBn) {}

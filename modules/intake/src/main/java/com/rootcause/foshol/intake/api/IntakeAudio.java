package com.rootcause.foshol.intake.api;

public record IntakeAudio(String filename, String contentType, byte[] bytes, int durationMs) {}

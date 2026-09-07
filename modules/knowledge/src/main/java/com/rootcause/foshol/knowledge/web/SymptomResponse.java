package com.rootcause.foshol.knowledge.web;

import java.util.UUID;

public record SymptomResponse(
        UUID id, String code, String nameBn, String nameEn, boolean nameEnFallback, String organ) {}

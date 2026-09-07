package com.rootcause.foshol.knowledge.application.query;

import java.util.UUID;

public record SymptomReadModel(UUID id, String code, String nameBn, String nameEn, String organ) {}

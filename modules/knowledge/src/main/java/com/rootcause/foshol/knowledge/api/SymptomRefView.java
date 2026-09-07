package com.rootcause.foshol.knowledge.api;

import java.util.UUID;

public record SymptomRefView(UUID id, String code, String nameBn, String nameEn, String organ) {}

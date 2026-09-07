package com.rootcause.foshol.knowledge.application.query;

import java.util.UUID;

public record CropReadModel(
        UUID id, String code, String nameBn, String nameEn, String iconKey, int displayOrder) {}

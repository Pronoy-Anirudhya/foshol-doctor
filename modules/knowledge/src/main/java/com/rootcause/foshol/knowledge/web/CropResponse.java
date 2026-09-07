package com.rootcause.foshol.knowledge.web;

import java.util.UUID;

public record CropResponse(
        UUID id,
        String code,
        String nameBn,
        String nameEn,
        boolean nameEnFallback,
        String iconKey,
        int displayOrder) {}

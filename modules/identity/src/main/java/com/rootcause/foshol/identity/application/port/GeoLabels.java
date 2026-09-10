package com.rootcause.foshol.identity.application.port;

public record GeoLabels(
        String divisionCode,
        String districtCode,
        String divisionNameEn,
        String divisionNameBn,
        String districtNameEn,
        String districtNameBn) {}

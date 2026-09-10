package com.rootcause.foshol.analysis.domain;

import java.util.UUID;

public record DiseaseNameRef(UUID id, String code, String nameBn, String nameEn, boolean healthy) {}

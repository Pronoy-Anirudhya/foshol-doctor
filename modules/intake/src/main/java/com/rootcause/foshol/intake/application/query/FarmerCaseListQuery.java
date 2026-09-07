package com.rootcause.foshol.intake.application.query;

import java.util.UUID;

public record FarmerCaseListQuery(UUID farmerId, int page, int size) {}

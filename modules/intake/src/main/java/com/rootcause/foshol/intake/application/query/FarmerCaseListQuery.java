package com.rootcause.foshol.intake.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record FarmerCaseListQuery(UUID farmerId, int page, int size) implements Query {}

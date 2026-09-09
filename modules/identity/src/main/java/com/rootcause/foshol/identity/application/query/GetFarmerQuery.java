package com.rootcause.foshol.identity.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record GetFarmerQuery(UUID officerId, UUID farmerId) implements Query {}

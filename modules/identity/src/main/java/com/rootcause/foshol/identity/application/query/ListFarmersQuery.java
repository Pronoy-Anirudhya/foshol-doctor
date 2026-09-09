package com.rootcause.foshol.identity.application.query;

import com.rootcause.foshol.common.cqrs.Query;
import java.util.UUID;

public record ListFarmersQuery(UUID officerId, int page, int size, String q, String phone) implements Query {}

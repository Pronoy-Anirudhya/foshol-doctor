package com.rootcause.foshol.identity.application.query;

import com.rootcause.foshol.common.cqrs.Query;

public record ListDistrictsQuery(String divisionCode) implements Query {}

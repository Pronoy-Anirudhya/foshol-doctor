package com.rootcause.foshol.knowledge.application.query;

import com.rootcause.foshol.common.cqrs.Query;

public record FindCropByCodeQuery(String code) implements Query {}

package com.rootcause.foshol.identity.application.query;

import com.rootcause.foshol.common.cqrs.Query;

public record MeQuery(java.util.UUID subjectId, String role) implements Query {}

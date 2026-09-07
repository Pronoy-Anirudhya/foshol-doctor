package com.rootcause.foshol.review.application.query;

import java.util.UUID;

public record OfficerQueueQuery(
        String state, boolean mine, UUID officerId, int page, int size, String sort, String order) {}

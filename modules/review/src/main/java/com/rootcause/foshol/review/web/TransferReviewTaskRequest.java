package com.rootcause.foshol.review.web;

import java.util.UUID;

public record TransferReviewTaskRequest(UUID targetOfficerId, Integer expectedVersion) {}

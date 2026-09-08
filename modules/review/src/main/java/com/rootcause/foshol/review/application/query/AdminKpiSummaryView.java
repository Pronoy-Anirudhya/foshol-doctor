package com.rootcause.foshol.review.application.query;

import java.util.List;
import java.util.UUID;

public record AdminKpiSummaryView(
        long assignmentFailures,
        long resolutionFailures,
        List<OfficerKpiCount> officers) {

    public record OfficerKpiCount(UUID officerId, String officerName, long resolutionFailures) {}
}

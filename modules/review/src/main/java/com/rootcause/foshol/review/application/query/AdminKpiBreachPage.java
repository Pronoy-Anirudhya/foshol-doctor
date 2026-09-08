package com.rootcause.foshol.review.application.query;

import com.rootcause.foshol.common.KpiKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminKpiBreachPage(
        List<AdminKpiBreachRow> content, int page, int size, long totalElements, int totalPages) {

    public record AdminKpiBreachRow(
            UUID id,
            UUID reviewTaskId,
            UUID caseId,
            KpiKind kind,
            UUID officerId,
            String officerName,
            String farmerName,
            String cropCode,
            String cropNameBn,
            Instant dueAt,
            Instant breachedAt) {}
}

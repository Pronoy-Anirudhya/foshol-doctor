package com.rootcause.foshol.review.application.port;

import com.rootcause.foshol.common.KpiKind;
import com.rootcause.foshol.review.domain.KpiBreach;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KpiBreachPort {

    boolean insertIfAbsent(KpiBreach breach);

    List<KpiOfficerCount> countResolutionByOfficer(String districtCode);

    long countByKind(String districtCode, KpiKind kind);

    List<KpiBreachRow> findBreaches(
            String districtCode, KpiKind kind, UUID officerId, int page, int size);

    long countBreaches(String districtCode, KpiKind kind, UUID officerId);

    record KpiOfficerCount(UUID officerId, long resolutionFailures) {}

    record KpiBreachRow(
            UUID id,
            UUID reviewTaskId,
            UUID caseId,
            String districtCode,
            KpiKind kind,
            UUID officerId,
            Instant windowStartedAt,
            Instant dueAt,
            Instant breachedAt,
            String farmerName,
            String cropCode,
            String cropNameBn) {}
}

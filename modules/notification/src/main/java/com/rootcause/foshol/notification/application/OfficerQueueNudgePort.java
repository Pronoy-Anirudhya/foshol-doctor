package com.rootcause.foshol.notification.application;

import java.util.UUID;
import java.time.Instant;

public interface OfficerQueueNudgePort {

    void emitQueue(UUID caseId, String toStatus, String correlationId, String districtCode);

    void emitKpi(UUID officerId, UUID caseId, UUID reviewTaskId, Instant dueAt, String correlationId);

    boolean farmerConnected(UUID farmerId);
}

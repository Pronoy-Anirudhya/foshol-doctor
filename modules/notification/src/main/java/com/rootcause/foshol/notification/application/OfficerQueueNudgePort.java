package com.rootcause.foshol.notification.application;

import java.util.UUID;

public interface OfficerQueueNudgePort {

    void emitQueue(UUID caseId, String toStatus, String correlationId, String districtCode);

    boolean farmerConnected(UUID farmerId);
}

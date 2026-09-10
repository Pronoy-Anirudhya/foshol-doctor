package com.rootcause.foshol.notification.application.command.handler;

import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.events.KpiWarningIssued;
import com.rootcause.foshol.notification.application.port.OfficerQueueNudgePort;
import org.springframework.stereotype.Service;

@Service
public class KpiWarningIssuedEventHandler {

    private final OfficerQueueNudgePort nudge;

    public KpiWarningIssuedEventHandler(OfficerQueueNudgePort nudge) {
        this.nudge = nudge;
    }

    public void handle(KpiWarningIssued event) {
        CorrelationId.set(event.correlationId());
        nudge.emitKpi(
                event.officerId(),
                event.caseId(),
                event.reviewTaskId(),
                event.dueAt(),
                event.correlationId());
    }
}

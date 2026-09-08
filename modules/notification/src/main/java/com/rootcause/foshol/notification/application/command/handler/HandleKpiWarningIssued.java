package com.rootcause.foshol.notification.application.command.handler;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.events.KpiWarningIssued;
import com.rootcause.foshol.notification.application.OfficerQueueNudgePort;
import org.springframework.stereotype.Service;

@Service
public class HandleKpiWarningIssued {

    private final OfficerQueueNudgePort nudge;

    public HandleKpiWarningIssued(OfficerQueueNudgePort nudge) {
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

package com.rootcause.foshol.notification.application.command.handler;

import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.events.ReviewTaskTransferred;
import com.rootcause.foshol.notification.application.port.OfficerQueueNudgePort;
import org.springframework.stereotype.Service;

@Service
public class ReviewTaskTransferredEventHandler {

    private final OfficerQueueNudgePort nudge;

    public ReviewTaskTransferredEventHandler(OfficerQueueNudgePort nudge) {
        this.nudge = nudge;
    }

    public void handle(ReviewTaskTransferred event) {
        CorrelationId.set(event.correlationId());
        nudge.emitQueue(event.caseId(), "CLAIMED", event.correlationId(), event.districtCode());
    }
}

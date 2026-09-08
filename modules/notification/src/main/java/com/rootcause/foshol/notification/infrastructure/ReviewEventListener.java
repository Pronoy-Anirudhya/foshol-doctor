package com.rootcause.foshol.notification.infrastructure;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.common.events.AdvisoryRevised;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.notification.application.command.handler.HandleAdvisoryApproved;
import com.rootcause.foshol.notification.application.command.handler.HandleAdvisoryRevised;
import com.rootcause.foshol.notification.application.command.handler.HandleCaseRejected;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReviewEventListener {

    private final HandleAdvisoryApproved approved;
    private final HandleAdvisoryRevised revised;
    private final HandleCaseRejected rejected;

    public ReviewEventListener(
            HandleAdvisoryApproved approved, HandleAdvisoryRevised revised, HandleCaseRejected rejected) {
        this.approved = approved;
        this.revised = revised;
        this.rejected = rejected;
    }

    @ApplicationModuleListener
    public void onApproved(AdvisoryApproved event) {
        CorrelationId.set(event.correlationId());
        log.info("event AdvisoryApproved caseId={}", event.caseId());
        approved.handle(event);
    }

    @ApplicationModuleListener
    public void onRevised(AdvisoryRevised event) {
        CorrelationId.set(event.correlationId());
        log.info("event AdvisoryRevised caseId={}", event.caseId());
        revised.handle(event);
    }

    @ApplicationModuleListener
    public void onRejected(CaseRejected event) {
        CorrelationId.set(event.correlationId());
        log.info("event CaseRejected caseId={}", event.caseId());
        rejected.handle(event);
    }
}

package com.rootcause.foshol.notification.infrastructure.listener;

import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.common.events.AdvisoryRevised;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.common.events.KpiBreached;
import com.rootcause.foshol.common.events.KpiWarningIssued;
import com.rootcause.foshol.common.events.ReviewTaskTransferred;
import com.rootcause.foshol.notification.application.command.handler.AdvisoryApprovedEventHandler;
import com.rootcause.foshol.notification.application.command.handler.AdvisoryRevisedEventHandler;
import com.rootcause.foshol.notification.application.command.handler.CaseRejectedEventHandler;
import com.rootcause.foshol.notification.application.command.handler.KpiBreachedEventHandler;
import com.rootcause.foshol.notification.application.command.handler.KpiWarningIssuedEventHandler;
import com.rootcause.foshol.notification.application.command.handler.ReviewTaskTransferredEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReviewEventListener {

    private final AdvisoryApprovedEventHandler approved;
    private final AdvisoryRevisedEventHandler revised;
    private final CaseRejectedEventHandler rejected;
    private final KpiWarningIssuedEventHandler kpiWarning;
    private final KpiBreachedEventHandler kpiBreached;
    private final ReviewTaskTransferredEventHandler transferred;

    public ReviewEventListener(
            AdvisoryApprovedEventHandler approved,
            AdvisoryRevisedEventHandler revised,
            CaseRejectedEventHandler rejected,
            KpiWarningIssuedEventHandler kpiWarning,
            KpiBreachedEventHandler kpiBreached,
            ReviewTaskTransferredEventHandler transferred) {
        this.approved = approved;
        this.revised = revised;
        this.rejected = rejected;
        this.kpiWarning = kpiWarning;
        this.kpiBreached = kpiBreached;
        this.transferred = transferred;
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

    @ApplicationModuleListener
    public void onKpiWarning(KpiWarningIssued event) {
        CorrelationId.set(event.correlationId());
        log.info("event KpiWarningIssued caseId={}", event.caseId());
        kpiWarning.handle(event);
    }

    @ApplicationModuleListener
    public void onKpiBreached(KpiBreached event) {
        CorrelationId.set(event.correlationId());
        kpiBreached.handle(event);
    }

    @ApplicationModuleListener
    public void onTransferred(ReviewTaskTransferred event) {
        CorrelationId.set(event.correlationId());
        log.info("event ReviewTaskTransferred caseId={}", event.caseId());
        transferred.handle(event);
    }
}

package com.rootcause.foshol.notification.infrastructure;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.notification.application.command.handler.HandleCaseStatusChanged;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IntakeEventListener {

    private final HandleCaseStatusChanged handler;

    public IntakeEventListener(HandleCaseStatusChanged handler) {
        this.handler = handler;
    }

    @ApplicationModuleListener
    public void onStatusChanged(CaseStatusChanged event) {
        CorrelationId.set(event.correlationId());
        log.info("event CaseStatusChanged caseId={} {} -> {}", event.caseId(), event.fromStatus(), event.toStatus());
        handler.handle(event);
    }
}

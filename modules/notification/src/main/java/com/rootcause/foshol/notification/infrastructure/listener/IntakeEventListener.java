package com.rootcause.foshol.notification.infrastructure.listener;

import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.notification.application.command.handler.CaseStatusChangedEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IntakeEventListener {

    private final CaseStatusChangedEventHandler handler;

    public IntakeEventListener(CaseStatusChangedEventHandler handler) {
        this.handler = handler;
    }

    @ApplicationModuleListener
    public void onStatusChanged(CaseStatusChanged event) {
        CorrelationId.set(event.correlationId());
        log.info("event CaseStatusChanged caseId={} {} -> {}", event.caseId(), event.fromStatus(), event.toStatus());
        handler.handle(event);
    }
}

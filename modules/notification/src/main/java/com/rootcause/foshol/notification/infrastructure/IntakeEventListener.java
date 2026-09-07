package com.rootcause.foshol.notification.infrastructure;

import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.notification.application.command.handler.HandleCaseStatusChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class IntakeEventListener {

    private final HandleCaseStatusChanged handler;

    public IntakeEventListener(HandleCaseStatusChanged handler) {
        this.handler = handler;
    }

    @ApplicationModuleListener
    public void onStatusChanged(CaseStatusChanged event) {
        handler.handle(event);
    }
}

package com.rootcause.foshol.notification.infrastructure;

import com.rootcause.foshol.common.events.AdvisoryApproved;
import com.rootcause.foshol.common.events.AdvisoryRevised;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.notification.application.command.HandleAdvisoryApproved;
import com.rootcause.foshol.notification.application.command.HandleAdvisoryRevised;
import com.rootcause.foshol.notification.application.command.HandleCaseRejected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

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
        approved.handle(event);
    }

    @ApplicationModuleListener
    public void onRevised(AdvisoryRevised event) {
        revised.handle(event);
    }

    @ApplicationModuleListener
    public void onRejected(CaseRejected event) {
        rejected.handle(event);
    }
}

package com.rootcause.foshol.notification.application.command.handler;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.events.KpiBreached;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HandleKpiBreached {

    private static final Logger log = LoggerFactory.getLogger(HandleKpiBreached.class);

    public void handle(KpiBreached event) {
        CorrelationId.set(event.correlationId());
        log.info(
                "kpi {} recorded caseId={} district={}",
                event.kind(),
                event.caseId(),
                event.districtCode());
    }
}

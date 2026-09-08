package com.rootcause.foshol.review.infrastructure;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.review.application.command.handler.SweepKpiCommandHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KpiSweeper {

    private final SweepKpiCommandHandler handler;

    public KpiSweeper(SweepKpiCommandHandler handler) {
        this.handler = handler;
    }

    @Scheduled(fixedDelayString = "${" + ConfigKeys.REVIEW_KPI_SWEEPER_INTERVAL + ":PT1M}")
    public void sweep() {
        handler.handle();
    }
}

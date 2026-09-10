package com.rootcause.foshol.review.infrastructure.listener;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.review.application.command.handler.SweepExpiredClaimsCommandHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClaimSweeper {

    private final SweepExpiredClaimsCommandHandler handler;

    public ClaimSweeper(SweepExpiredClaimsCommandHandler handler) {
        this.handler = handler;
    }

    @Scheduled(fixedDelayString = "${" + ConfigKeys.REVIEW_SWEEPER_INTERVAL + "}")
    public void sweep() {
        handler.handle();
    }
}

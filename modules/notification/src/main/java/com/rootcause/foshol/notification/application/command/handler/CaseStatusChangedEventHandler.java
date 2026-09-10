package com.rootcause.foshol.notification.application.command.handler;

import com.rootcause.foshol.common.util.CorrelationId;
import com.rootcause.foshol.common.enums.NotificationType;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.common.events.CaseStatusChanged;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.notification.application.command.DeliveryService;
import com.rootcause.foshol.notification.application.command.NotificationContentAssembler;
import com.rootcause.foshol.notification.application.port.NotificationRepository;
import com.rootcause.foshol.notification.application.port.OfficerQueueNudgePort;
import com.rootcause.foshol.notification.domain.Notification;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CaseStatusChangedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CaseStatusChangedEventHandler.class);

    private final FarmerLookupApi farmers;
    private final NotificationRepository notifications;
    private final NotificationContentAssembler assembler;
    private final DeliveryService delivery;
    private final OfficerQueueNudgePort nudge;
    private final Clock clock;

    public CaseStatusChangedEventHandler(
            FarmerLookupApi farmers,
            NotificationRepository notifications,
            NotificationContentAssembler assembler,
            DeliveryService delivery,
            OfficerQueueNudgePort nudge,
            Clock clock) {
        this.farmers = farmers;
        this.notifications = notifications;
        this.assembler = assembler;
        this.delivery = delivery;
        this.nudge = nudge;
        this.clock = clock;
    }

    public void handle(CaseStatusChanged event) {
        CorrelationId.set(event.correlationId());
        String district = "";
        try {
            Optional<FarmerView> farmer = farmers.findById(event.farmerId());
            if (farmer.isEmpty()) {
                log.warn("Unknown farmer on CaseStatusChanged correlationId={}", event.correlationId());
                return;
            }
            district = farmer.get().districtCode() == null ? "" : farmer.get().districtCode();
            if (notifications
                    .findDuplicate(
                            event.farmerId(),
                            event.caseId(),
                            NotificationType.CASE_STATUS_CHANGED,
                            event.toStatus().name())
                    .isPresent()) {
                return;
            }
            var content = assembler.assembleStatus(event.fromStatus().name(), event.toStatus().name(), farmer.get());
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("correlationId", event.correlationId());
            payload.put("fromStatus", event.fromStatus().name());
            payload.put("toStatus", event.toStatus().name());
            delivery.deliver(Notification.pending(
                    Uuid7.create(),
                    event.farmerId(),
                    event.caseId(),
                    null,
                    NotificationType.CASE_STATUS_CHANGED,
                    content.titleBn(),
                    content.bodyBn(),
                    payload,
                    clock.instant()));
        } finally {
            nudge.emitQueue(event.caseId(), event.toStatus().name(), event.correlationId(), district);
        }
    }
}

package com.rootcause.foshol.notification.application.command;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.events.CaseRejected;
import com.rootcause.foshol.identity.api.FarmerLookupApi;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.notification.application.DeliveryService;
import com.rootcause.foshol.notification.application.NotificationContentAssembler;
import com.rootcause.foshol.notification.application.NotificationRepository;
import com.rootcause.foshol.notification.domain.Notification;
import com.rootcause.foshol.review.api.RejectionView;
import com.rootcause.foshol.review.api.ReviewSubmissionApi;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HandleCaseRejected {

    private static final Logger log = LoggerFactory.getLogger(HandleCaseRejected.class);

    private final FarmerLookupApi farmers;
    private final ReviewSubmissionApi review;
    private final NotificationRepository notifications;
    private final NotificationContentAssembler assembler;
    private final DeliveryService delivery;
    private final Clock clock;

    public HandleCaseRejected(
            FarmerLookupApi farmers,
            ReviewSubmissionApi review,
            NotificationRepository notifications,
            NotificationContentAssembler assembler,
            DeliveryService delivery,
            Clock clock) {
        this.farmers = farmers;
        this.review = review;
        this.notifications = notifications;
        this.assembler = assembler;
        this.delivery = delivery;
        this.clock = clock;
    }

    public void handle(CaseRejected event) {
        CorrelationId.set(event.correlationId());
        Optional<FarmerView> farmer = farmers.findById(event.farmerId());
        if (farmer.isEmpty()) {
            log.warn("Unknown farmer on CaseRejected correlationId={}", event.correlationId());
            return;
        }
        if (notifications
                .findDuplicate(event.farmerId(), event.caseId(), NotificationType.CASE_REJECTED, event.caseId().toString())
                .isPresent()) {
            return;
        }
        RejectionView rejection = review.findRejection(event.caseId())
                .orElse(new RejectionView(
                        event.caseId(),
                        event.officerId(),
                        event.officerName(),
                        event.reasonCode(),
                        event.messageBn(),
                        event.occurredAt()));
        var content = assembler.assembleRejection(rejection, farmer.get());
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("correlationId", event.correlationId());
        payload.put("reasonCode", event.reasonCode().name());
        delivery.deliver(Notification.pending(
                Uuid7.create(),
                event.farmerId(),
                event.caseId(),
                null,
                NotificationType.CASE_REJECTED,
                content.titleBn(),
                content.bodyBn(),
                payload,
                clock.instant()));
    }
}
